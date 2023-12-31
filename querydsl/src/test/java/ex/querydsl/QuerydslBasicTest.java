package ex.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.*;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import ex.querydsl.dto.MemberDto;
import ex.querydsl.dto.QMemberDto;
import ex.querydsl.dto.UserDto;
import ex.querydsl.entity.Member;
import ex.querydsl.entity.QMember;
import ex.querydsl.entity.Team;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.querydsl.jpa.JPAExpressions.*;
import static ex.querydsl.entity.QMember.*;
import static ex.querydsl.entity.QTeam.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    void before() {

        queryFactory = new JPAQueryFactory(em);

        Team teamA = new Team("TeamA");
        Team teamB = new Team("TeamB");
        em.persist(teamA);
        em.persist(teamB);

        Member memberA = Member.builder().username("MemberA").age(20).team(teamA).build();
        Member memberB = Member.builder().username("MemberB").age(30).team(teamA).build();
        Member memberC = Member.builder().username("MemberC").age(40).team(teamB).build();
        Member memberD = Member.builder().username("MemberD").age(50).team(teamB).build();

        em.persist(memberA);
        em.persist(memberB);
        em.persist(memberC);
        em.persist(memberD);
    }

    //JPQL 과 QueryDsl 비교
    @Test
    void jpql_VS_querydsl() {

        //JPQL
        //쿼리를 문자로 작성(런타임 시점에 오류)
        //파라미터 바인딩 직접
        String query = "select m from Member m where m.username = :username";

        Member findByJpql = em.createQuery(query, Member.class)
                .setParameter("username", "MemberA")
                .getSingleResult();

        assertThat(findByJpql.getUsername()).isEqualTo("MemberA");

        //QueryDsl
        //쿼리를 빌더로 작성(컴파일 시점에 오류)
        //파라미터 바인딩 자동 처리
        //QMember member = new QMember("m1"); - 별칭 직접 지정, 같은 테이블을 조인해야 하는 경우 아니면 기본 인스턴스 사용 권장
        Member findByQuerydsl = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("MemberB"))
                .fetchOne();

        assertThat(findByQuerydsl.getUsername()).isEqualTo("MemberB");
    }

    //검색 조건
    @Test
    void search() {

        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("MemberA")
                        .and(member.age.eq(20)) //검색 조건은 and(콤마로도 가능), or 메서드로 체인 가능
                )
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("MemberA");
        assertThat(findMember.getAge()).isEqualTo(20);

        /**
           member.username.eq("memberA") // username = 'memberA'
           member.username.ne("memberA") //username != 'memberA'
           member.username.eq("memberA").not() // username != 'memberA'
           member.username.isNotNull() //이름이 is not null
           member.age.in(10, 20) // age in (10,20)
           member.age.notIn(10, 20) // age not in (10, 20)
           member.age.between(10,30) //between 10, 30
           member.age.goe(30) // age >= 30
           member.age.gt(30) // age > 30
           member.age.loe(30) // age <= 30
           member.age.lt(30) // age < 30
           member.username.like("member%") //like 검색
           member.username.contains("member") // like ‘%member%’ 검색
           member.username.startsWith("member") //like ‘member%’ 검색
         */
    }

    //결과 조회
    @Test
    void result() {

        //List
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

        //단 건
        Member findMember1 = queryFactory
                .selectFrom(member)
                .fetchOne(); //NonUniqueResultException

        //처음 한 건
        Member findMember2 = queryFactory
                .selectFrom(member)
                .fetchFirst();

        //fetchResults - 페이징 정보 포함
        QueryResults<Member> results1 = queryFactory
                .selectFrom(member)
                .fetchResults(); //Deprecated - count 쿼리 추가로 발생, 복잡한 쿼리에서 제대로 동작하지 않을 수 있음 > count 쿼리를 따로 날리는 방식을 권장

        //count
        long count1 = queryFactory
                .selectFrom(member)
                .fetchCount(); //Deprecated

        Long count2 = queryFactory
                .select(member.count())
                .from(member)
                .fetchOne();

        Long count3 = queryFactory
                .select(Wildcard.count) //count(*)
                .from(member)
                .fetchOne();
    }

    //정렬
    @Test
    void sort() {

        em.persist(Member.builder().username("MemberE").age(100).build());
        em.persist(Member.builder().username("MemberF").age(90).build());
        em.persist(Member.builder().age(90).build());

        //나이 내림차순, 이름 오름차순, 이름이 없으면 마지막
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.between(90, 100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        for (Member member : result) {
            System.out.println("member age = " + member.getAge() + " name = " + member.getUsername());
        }

        Member memberE = result.get(0);
        Member memberF = result.get(1);
        Member memberNull = result.get(2);
        assertThat(memberE.getUsername()).isEqualTo("MemberE");
        assertThat(memberF.getUsername()).isEqualTo("MemberF");
        assertThat(memberNull.getUsername()).isNull();
    }

    //페이징
    @Test
    void paging() {

        List<Member> result1 = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result1.size()).isEqualTo(2);

        //total count 가 필요하다면 fetchResults - count 쿼리를 추가로 실행
        //fetchResults Deprecated
        //추가로 실행되는 count 쿼리는 단순히 select 구문을 count 처리 용도로 바꾼 것으로
        //단순한 쿼리에서는 잘 동작하지만 복잡한 쿼리에서는 제대로 동작하지 않을 수 있다
        //추가로 //원본 쿼리와 똑같이 조인을 해버리기 때문에 성능이 안나올 수 있다
        //따라서 count 쿼리가 필요하다면 별도로 작성해야 한다
        QueryResults<Member> result2 = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(result2.getTotal()).isEqualTo(4);

        Long count1 = queryFactory
                .select(member.count())
                .from(member)
                .fetchOne();

        assertThat(count1).isEqualTo(4);

        Long count2 = queryFactory
                .select(Wildcard.count) //count(*)
                .from(member)
                .fetchOne();

        assertThat(count1).isEqualTo(4);
    }

    //집합 & 집계
    @Test
    void aggregation() {

        List<Tuple> result1 = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result1.get(0);

        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(140);
        assertThat(tuple.get(member.age.avg())).isEqualTo(35);
        assertThat(tuple.get(member.age.max())).isEqualTo(50);
        assertThat(tuple.get(member.age.min())).isEqualTo(20);

        //groupBy & having 팀의 이름과 각 팀의 평균 나이
        List<Tuple> result2 = queryFactory
                .select(
                        team.name,
                        member.age.avg()
                )
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
//                .having(member.age.avg().gt(30))
                .fetch();

        Tuple teamA = result2.get(0);
        Tuple teamB = result2.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("TeamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(25);
        assertThat(teamB.get(team.name)).isEqualTo("TeamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(45);
    }

    @Test
    void join() {

        //팀 A에 소속된 모든 회원
        List<Member> teamAMember = queryFactory
                .selectFrom(member)
                .join(member.team, team) //inner join
                .where(team.name.eq("TeamA"))
                .fetch();

        assertThat(teamAMember)
                .extracting("username")
                .containsExactly("MemberA", "MemberB");


        //세타 조인(일명 막조인) - 연관관계가 없는 필드로 조인
        //유저이름과 팀명이 같은 경우
        em.persist(Member.builder().username("TeamA").build());
        em.persist(Member.builder().username("TeamB").build());
        em.persist(Member.builder().username("TeamC").build());

        List<Member> thetaJoinResult = queryFactory
                .select(member)
                .from(member, team) //from 절에 여러 엔티티를 선택해서 세타 조인 - 외부 조인은 불가능 > on 절 사용으로 해결
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(thetaJoinResult)
                .extracting("username")
                .containsExactly("TeamA", "TeamB");
    }

    //조인 + on 절
    @Test
    void joinWithOn() {

        //조인 대상 필터링 - 멤버는 모두 조회하고 팀은 이름이 TeamA 인 팀만 조회
        List<Tuple> leftJoinResult = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team)
                .on(team.name.eq("TeamA")) //inner join 이면 where 과 동일
                .fetch();

        for (Tuple tuple : leftJoinResult) {
            System.out.println("tuple = " + tuple);
        }

        //연관관계가 없는 경우 left join(막조인)
        em.persist(Member.builder().username("TeamA").build());
        em.persist(Member.builder().username("TeamB").build());
        em.persist(Member.builder().username("TeamC").build());

        List<Tuple> noRelationLeftJoinResult = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team) //일반 조인과 다르게 엔티티 하나만 들어간다
                .on(member.username.eq(team.name))
                .fetch();

        for (Tuple tuple : noRelationLeftJoinResult) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    //페치 조인
    @Test
    void fetchJoin() {

        em.flush();
        em.clear();

        //페치 조인 미사용
        Member lazyMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("MemberA"))
                .fetchOne();

        boolean isLoaded1 = emf.getPersistenceUnitUtil().isLoaded(lazyMember.getTeam()); //로딩이된 엔티티인지 아닌지
        assertThat(isLoaded1).isFalse();

        em.flush();
        em.clear();

        //페치 조인 사용
        Member fetchMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("MemberA"))
                .fetchOne();

        boolean isLoaded2 = emf.getPersistenceUnitUtil().isLoaded(fetchMember.getTeam()); //로딩이된 엔티티인지 아닌지
        assertThat(isLoaded2).isTrue();
    }

    //서브 쿼리
    @Test
    void subQuery() {

        QMember subMember = new QMember("subMember"); //별칭 다르게 지정

        //나이가 가장 많은 회원
        List<Member> result1 = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        select(subMember.age.max())
                                .from(subMember)
                ))
                .fetch();

        assertThat(result1)
                .extracting("age")
                .containsExactly(50);

        //나이가 평균 이상인 회원
        List<Member> result2 = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        select(subMember.age.avg())
                                .from(subMember)
                ))
                .fetch();

        assertThat(result2)
                .extracting("age")
                .containsExactly(40, 50);

        //서브쿼리 in 절 사용
        List<Member> result3 = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        select(subMember.age)
                                .from(subMember)
                                .where(subMember.age.gt(20))
                ))
                .fetch();

        assertThat(result3)
                .extracting("age")
                .containsExactly(30, 40, 50);

        //select 절 서브쿼리
        List<Tuple> result4 = queryFactory
                .select(
                        member.username,
                        select(subMember.age.avg())
                                .from(subMember)
                )
                .from(member)
                .fetch();

        for (Tuple tuple : result4) {
            System.out.println("tuple = " + tuple);
        }

        /**
         * from 절의 서브쿼리 한계
         * JPQL 서브쿼리의 한계로 인해 from 절의 서브쿼리(인라인 뷰)는 지원하지 않는다.
         * 당연히 querydsl 도 지원하지 않는다
         *
         * from 절 서브쿼리 해결방안
         * - 서브쿼리를 join 으로 변경한다(불가능한 상황도 있다)
         * - 애플리케이션에서 쿼리를 분리해서 실행한다
         * - 네이티브 쿼리를 사용한다
         *
         * 한방쿼리의 미신?
         * 화면에서 보여주기 위한 기능에 집중한 쿼리는 서브쿼리가 많아져서 매우 복잡해질 수 있다
         * DB는 데이터를 최소화해서 가져오는 역할에 집중해야 한다
         * 서비스 로직들은 애플리케이션 내에서 해결하고 프레젠테이션 로직은 프레젠테이션에서 끝내야 한다
         */
    }

    //CASE 문
    @Test
    void caseExamples() {

        //단순한 조건
        List<String> result1 = queryFactory
                .select(
                        member.age
                                .when(20).then("스무살")
                                .when(30).then("서른살")
                                .otherwise("기타")
                )
                .from(member)
                .fetch();

        for (String s : result1) {
            System.out.println("age = " + s);
        }

        //복잡한 조건
        List<String> result2 = queryFactory
                .select(
                        new CaseBuilder()
                                .when(member.age.between(0, 20)).then("0 ~ 20살")
                                .when(member.age.between(21, 40)).then("21 ~ 40살")
                                .otherwise("41살 이상")
                )
                .from(member)
                .fetch();

        for (String s : result2) {
            System.out.println("age = " + s);
        }
    }

    //orderBy + Case
    @Test
    void caseWithOrderBy() {

        NumberExpression<Integer> rankPath = new CaseBuilder()
                .when(member.age.between(0, 30)).then(1)
                .when(member.age.between(31, 40)).then(2)
                .otherwise(3);

        List<Tuple> result = queryFactory
                .select(
                        member.username,
                        member.age,
                        rankPath
                )
                .from(member)
                .orderBy(rankPath.desc())
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            Integer rank = tuple.get(rankPath);
            System.out.println("username = " + username + " age = " + age + " rank = " + rank);
        }
    }

    //상수 더하기
    @Test
    void constant() {

        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    //문자 더하기
    @Test
    void concat() {

        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue())) //문자가 아닌 타입을 문자로 변환
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("user = " + s);
        }
    }

    //프로젝션
    @Test
    void projections() {

        //프로젝션 대상이 하나
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("username = " + s);
        }

        //대상이 둘 이상
        //튜플로 조회
        List<Tuple> tupleResult = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : tupleResult) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username + " age = " + age);
        }
    }

    //프로젝션 - DTO
    @Test
    void projectionByDto() {

        //JPQL
        //new 명령서를 사용해야 하기에 코드가 지저분해진다
        //생성자 방식만 지원한다
        List<MemberDto> jpqlDtoResult = em.createQuery(
                    "select " +
                                 "new ex.querydsl.dto.MemberDto(" +
                                     "m.username, " +
                                     "m.age" +
                                 ") " +
                             "from " +
                                 "Member m", MemberDto.class
                             ).getResultList();

        for (MemberDto memberDto : jpqlDtoResult) {
            System.out.println("jpql result= " + memberDto);
        }

        //QueryDsl
        //프로퍼티(Setter) 접근 방식
        List<MemberDto> setterResult = queryFactory
                .select(
                        Projections.bean(MemberDto.class,
                                member.username,
                                member.age
                        )
                )
                .from(member)
                .fetch();

        for (MemberDto memberDto : setterResult) {
            System.out.println("setter result = " + memberDto);
        }

        //필드 접근 방식
        List<MemberDto> fieldResult = queryFactory
                .select(
                        Projections.fields(MemberDto.class,
                                member.username,
                                member.age
                        )
                )
                .from(member)
                .fetch();

        for (MemberDto memberDto : fieldResult) {
            System.out.println("field result = " + memberDto);
        }

        //만약 별칭이 다를 경우
        QMember subMember = new QMember("subMember");
        List<UserDto> aliasResult = queryFactory
                .select(
                        Projections.fields(UserDto.class,
                                member.username.as("name"), //필드에 별칭 지정
                                ExpressionUtils.as(JPAExpressions //필드나 서브 쿼리 별칭 지정
                                                .select(subMember.age.max())
                                                .from(subMember), "age")
                        )
                )
                .from(member)
                .fetch();

        for (UserDto userDto : aliasResult) {
            System.out.println("alias result = " + userDto);
        }

        //생성자 접근 방식
        List<MemberDto> constructorResult = queryFactory
                .select(
                        Projections.constructor(MemberDto.class, //생성자와 타입이 딱 맞아야 한다
                                member.username,
                                member.age
                        )
                )
                .from(member)
                .fetch();

        for (MemberDto memberDto : constructorResult) {
            System.out.println("constructor result = " + memberDto);
        }
    }

    //@QueryProjection
    @Test
    void queryProjection() {

        //컴파일러로 타입 체크 가능 > 가장 안전함
        //단점으로는 dto 클래스가 querydsl 의존성을 가지게 되고 dto 까지 Q 클래스 파일로 생성해야 한다
        List<MemberDto> result = queryFactory
                .select(
                        new QMemberDto(
                                member.username,
                                member.age
                        )
                )
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("result = " + memberDto);
        }
    }

    //동적쿼리 - BooleanBuilder
    @Test
    void booleanBuilder() {

        String username = "MemberA";
        Integer age = 20;

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(
                        searchCondition(username, age)
                )
                .fetch();

        assertThat(result.size()).isEqualTo(1);
    }

    private BooleanBuilder searchCondition(String username, Integer age) {
        BooleanBuilder builder = new BooleanBuilder();
        if(username != null){
            builder.and(member.username.eq(username));
        }
        if(age != null){
            builder.and(member.age.eq(age));
        }
        return builder;
    }

    //동적쿼리 where 다중 파라미터
    @Test
    void dynamicQuery() {

        String username = "MemberA";
        Integer age = 20;

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(
//                        searchByUsername(username), //where 조건의 null 은 무시된다
//                        searchByAge(age),
                        searchByUsernameAndAge(username, age) //메서드를 재활용 할 수 있고 가독성이 좋아짐
                )
                .fetch();

        assertThat(result.size()).isEqualTo(1);
    }

    private Predicate searchByUsernameAndAge(String username, Integer age){
        return username == null || age == null ? null : searchByUsername(username).and(searchByAge(age));
    }

    private BooleanExpression searchByUsername(String username) {
        return username == null ? null :member.username.eq(username);
    }

    private BooleanExpression searchByAge(Integer age) {
        return age == null ? null :member.age.eq(age);
    }

    //벌크 연산
    @Test
    void bulkOperation() {

        long updatedCount = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(25))
                .execute();

        assertThat(updatedCount).isEqualTo(1);

        //        long addedCount = queryFactory
//                .update(member)
//                .set(member.age, member.age.add(1))
//                .set(member.age, member.age.multiply(2))
//                .execute();

        //영속성 컨텍스트에 있는 엔티티를 무시하고 실행되기 때문에
        //벌크 연산 실행 후 영속성 컨텍스트를 초기화하는 것이 안전하다
        em.flush();
        em.clear();

        long deletedCount = queryFactory
                .delete(member)
                .where(member.age.lt(25))
                .execute();

        assertThat(deletedCount).isEqualTo(1);
    }

    //SQL 함수 호출
    @Test
    void callSqlFunction() {

        List<String> replaceResult = queryFactory
                .select(
                        //Dialect 에 등록된 함수만 호출 가능
                        Expressions.stringTemplate(
                                "function('replace', {0}, {1}, {2})",
                                member.username, "Member", "M")
                )
                .from(member)
                .fetch();

        for (String s : replaceResult) {
            System.out.println("username = " + s);
        }

        List<String> lowerResult  = queryFactory
                .select(member.username)
                .from(member)
//                .where(
//                        member.username.eq(
//                                Expressions.stringTemplate("function('lower', {0})", member.username)
//                        )
//                )
                //대부분의 ansi 표준 함수들은 querydsl 이 내장하고 있다
                .where(member.username.eq(member.username.lower()))
                .fetch();
    }

}