package com.icloud.filter;

import co.elastic.logstash.api.Event;
import co.elastic.logstash.api.Filter;
import co.elastic.logstash.api.FilterMatchListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.logstash.plugins.ConfigurationImpl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.*;

class NoriTest {

    @DisplayName("사용자가 설정한 필드 실패 테스트")
    @Test
    void testWrongFieldsSetting() {

        // GIVEN
        var configMap = Map.<String, Object>ofEntries(
                entry(Nori.FIELDS.name(), List.of("field1", "field2", "field3")),
                entry(Nori.EXTRACT_TAGS.name(), getTestExtractTags())
        );
        Filter noriFilter = getFilter(configMap);
        var event = getEvent();
        event.setField("field1", "형태소(形態素, 영어: morpheme)는 언어학에서 (일반적인 정의를 따르면) 일정한 의미가 있는 가장 작은 말의 단위로 발화체 내에서 따로 떼어낼 수 있는 것을 말한다.");
        event.setField("field2", " 이번 포스팅에선 Elasticsearch 를 사용할 때 클러스터에 적절한 샤드 개수를 결정하는 방법들에 대해 소개하겠다.\n내용이 길어 여러 포스트에 걸쳐 소개할 예정이다.");

        // WHEN
        noriFilter.filter(Collections.singletonList(event), getTestMatchListener());


        // THEN

        // field1, field2는 존재하는 필드이기 때문에 null이 아니어야 함.
        assertNotNull(event.getField("field1_morpheme"));
        assertNotNull(event.getField("field2_morpheme"));

        // field3는 존재하는 필드가 아니기 때문에 tags에 해당 오류 메시지가 담겨야 함.
        assertNull(event.getField("field3_morpheme"));
        assertEquals("[field3]'s value is null", ((List<?>) event.getField("tags")).get(0));
    }

    @DisplayName("사용자가 설정한 필드 정상 테스트")
    @Test
    void testCorrectFieldsSetting() {

        // GIVEN
        var configMap = Map.<String, Object>ofEntries(
                entry(Nori.FIELDS.name(), List.of("field1", "field2", "field3")),
                entry(Nori.EXTRACT_TAGS.name(), getTestExtractTags())
        );
        Filter noriFilter = getFilter(configMap);
        var event = getEvent();
        event.setField("field1", "형태소(形態素, 영어: morpheme)는 언어학에서 (일반적인 정의를 따르면) 일정한 의미가 있는 가장 작은 말의 단위로 발화체 내에서 따로 떼어낼 수 있는 것을 말한다.");
        event.setField("field2", "이번 포스팅에선 Elasticsearch 를 사용할 때 클러스터에 적절한 샤드 개수를 결정하는 방법들에 대해 소개하겠다.\n내용이 길어 여러 포스트에 걸쳐 소개할 예정이다.");
        event.setField("field3", "가슴 속에 하나 둘 새겨지는 별을\n" +
                                 "이제 다 못 헤는 것은\n" +
                                 "쉬이 아침이 오는 까닭이요,\n" +
                                 "내일 밤이 남은 까닭이요,\n" +
                                 "아직 나의 청춘이 다하지 않은 까닭입니다.\n");

        // WHEN
        noriFilter.filter(Collections.singletonList(event), getTestMatchListener());


        // THEN

        // field1, field2, field3 모두 존재하는 필드이기 때문에 null이 아니어야 함.
        assertNotNull(event.getField("field1_morpheme"));
        assertNotNull(event.getField("field2_morpheme"));
        assertNotNull(event.getField("field3_morpheme"));

        // 오류 요소들은 하나도 없기 때문에 tags는 null이어야 함.
        assertNull(event.getField("tags"));
    }

    @DisplayName("품사 태그 추출 설정을 하지 않을 시 형태소 배열의 크기는 0이어야 한다.")
    @Test
    void testExtractTagsNothing() {
        // GIVEN
        var configMap = Map.<String, Object>ofEntries(
                entry(Nori.FIELDS.name(), List.of("field1"))
        );
        Filter noriFilter = getFilter(configMap);
        var event = getEvent();
        event.setField("field1", "가슴 속에 하나 둘 새겨지는 별을\n" +
                                 "이제 다 못 헤는 것은\n" +
                                 "쉬이 아침이 오는 까닭이요,\n" +
                                 "내일 밤이 남은 까닭이요,\n" +
                                 "아직 나의 청춘이 다하지 않은 까닭입니다.\n");

        // WHEN
        noriFilter.filter(Collections.singletonList(event), getTestMatchListener());

        // THEN

        // 추출하려는 태그 정보가 없기 때문에 형태소 배열의 크기는 0이어야 한다.
        assertEquals(0, ((List<?>) event.getField("field1_morpheme")).size());
    }

    @DisplayName("품사 태그 추출 설정을 할 경우 지정한 품사 태그에 해당하는 형태소만 분리해야 한다.")
    @Test
    void testExtractTagsSomething() {
        // GIVEN
        var configMap = Map.<String, Object>ofEntries(
                entry(Nori.FIELDS.name(), List.of("field1")),
                // 주격 조사만 추출하도록 설정
                entry(Nori.EXTRACT_TAGS.name(), List.of("JKS"))
        );
        Filter noriFilter = getFilter(configMap);
        var event = getEvent();
        event.setField("field1", "가슴 속에 하나 둘 새겨지는 별을\n" +
                                 "이제 다 못 헤는 것은\n" +
                                 "쉬이 아침이 오는 까닭이요,\n" +
                                 "내일 밤이 남은 까닭이요,\n" +
                                 "아직 나의 청춘이 다하지 않은 까닭입니다.\n");

        // WHEN
        noriFilter.filter(Collections.singletonList(event), getTestMatchListener());

        // THEN
        assertArrayEquals(((List<?>) event.getField("field1_morpheme")).toArray(),
                List.of("에", "을", "은", "이", "이", "의", "이").toArray());
    }

    @DisplayName("사용자 사전 설정을 잘못 구성한 경우 예외를 던져야 한다.")
    @Test
    void testWrongPathUserDictionary() {
        // GIVEN
        var configMap = Map.ofEntries(
                entry(Nori.FIELDS.name(), List.of("field1")),
                entry(Nori.EXTRACT_TAGS.name(), getTestExtractTags()),
                entry(Nori.USER_DICTIONARY_PATH.name(), "wrong_user_dictionary_path")
        );

        String message = assertThrows(RuntimeException.class, () -> {
            getFilter(configMap);
        }).getLocalizedMessage();
        System.out.println("message = " + message);
    }

    @DisplayName("사용자 사전 설정을 올바르게 구성한 경우 형태소 분석은 사용자 사전을 따라야 한다.")
    @Test
    void testCorrectPathUserDictionary() {
        // GIVEN
        var configMap = Map.ofEntries(
                entry(Nori.FIELDS.name(), List.of("field1")),
                entry(Nori.EXTRACT_TAGS.name(), getTestExtractTags()),
                entry(Nori.USER_DICTIONARY_PATH.name(), getTestDictionaryPath())
        );

        Filter noriFilter = getFilter(configMap);
        Event event = getEvent();
        // 텍스트 출처 : https://www.cardif.co.kr/life-stage/safe-start/cmsContents.do?cmsPath=/cmsHtml/life-stage/LSSAF002M/D0021/life_stage_content.html
        event.setField("field1", "Z세대 신조어 세번째! ‘일며들다’입니다. ‘일이 내 삶에 스며들었다’ 의 줄임말로 일이 24시간 직장인의 곁을 떠나지 않는 현실을 반영한 신조어입니다. 야근이나 상사의 업무 외 연락, 식사 등이 ‘일며드는’ 느낌을 주는 가장 큰 이유로 꼽히는데요. 업무를 마치고 나서도 계속되는 업무 생각과 걱정도 일종의 ‘일며드는’ 현상으로 볼 수 있습니다. ‘워라밸’, ‘일과 삶의 분리’가 명확한 Z세대 특성 상 해당 신조어는 부정적인 의미에 가까운 점도 참고해두시면 좋겠습니다.");

        // WHEN
        noriFilter.filter(Collections.singletonList(event), getTestMatchListener());

        // THEN
        assertTrue(((List<?>) event.getField("field1_morpheme")).contains("일며들다"));
    }

    @DisplayName("합성어 설정을 아무것도 하지 않을 시 none이 되어야 한다.")
    @Test
    void testDefaultDecompoundMode() {
        // GIVEN
        var configMap = Map.<String, Object>ofEntries(
                entry(Nori.FIELDS.name(), List.of("field1")),
                entry(Nori.EXTRACT_TAGS.name(), getTestExtractTags())
        );
        Filter noriFilter = getFilter(configMap);
        Event event = getEvent();
        event.setField("field1", "삼성전자");

        // WHEN
        noriFilter.filter(Collections.singletonList(event), getTestMatchListener());

        // THEN

        // 삼성전자는 삼성 + 전자의 합성어로 취급하지만 합성어 설정을 하지 않았기 때문에 none으로 설정,
        // 즉, 분리하지 않음.
        assertEquals(1, ((List<?>) event.getField("field1_morpheme")).size());
        assertEquals("삼성전자", ((List<?>) event.getField("field1_morpheme")).get(0));
    }

    @DisplayName("잘못된 합성어 설정을 한 경우 예외를 발생시켜야 한다.")
    @Test
    void testWrongDecompoundMode() {

        // GIVEN
        var configMap = Map.ofEntries(
                entry(Nori.FIELDS.name(), List.of("field")),
                entry(Nori.DECOMPOUND_MODE.name(), "wrong_decompound_mode"),
                entry(Nori.EXTRACT_TAGS.name(), getTestExtractTags())
        );

        // WHEN & THEN
        String message = assertThrows(IllegalArgumentException.class, () -> {
            getFilter(configMap);
        }).getMessage();
        System.out.println("message = " + message);
    }

    @DisplayName("합성어 설정을 한 경우 해당 설정으로 형태소를 분리해야 한다.")
    @Test
    void testSpecifiedDecompoundMode() {
        // GIVEN
        var configMap = Map.<String, Object>ofEntries(
                entry(Nori.FIELDS.name(), List.of("field1")),
                entry(Nori.DECOMPOUND_MODE.name(), "mixed"),
                entry(Nori.EXTRACT_TAGS.name(), getTestExtractTags())
        );
        Filter noriFilter = getFilter(configMap);
        Event event = getEvent();
        event.setField("field1", "삼성전자");

        // WHEN
        noriFilter.filter(Collections.singletonList(event), getTestMatchListener());

        // THEN

        assertEquals(3, ((List<?>) event.getField("field1_morpheme")).size());
        assertArrayEquals(((List<?>) event.getField("field1_morpheme")).toArray(), List.of("삼성전자", "삼성", "전자").toArray());
    }

    private Filter getFilter(Map<String, Object> configMap) {
        return new Nori(getTestId(), new ConfigurationImpl(configMap), null);
    }

    private Event getEvent() {
        return new org.logstash.Event();
    }

    private TestMatchListener getTestMatchListener() {
        return new TestMatchListener();
    }

    private String getTestId() {
        return "test-id";
    }


    private List<String> getTestExtractTags() {
        return List.of("NNP", "NNG");
    }

    private String getTestDictionaryPath() {
        return ClassLoader.getSystemResource("test_user_dictionary.txt").getPath();
    }

    static class TestMatchListener implements FilterMatchListener {
        private final AtomicInteger matchCount = new AtomicInteger(0);

        @Override
        public void filterMatched(Event event) {
            matchCount.incrementAndGet();
        }

        public int getMatchCount() {
            return matchCount.get();
        }
    }


}