package com.icloud.filter;

import co.elastic.logstash.api.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.ko.POS;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.lucene.analysis.ko.KoreanTokenizer.DecompoundMode;

@LogstashPlugin(name = "nori")
public class Nori implements Filter {

    /**
     * 형태소 분석을 할 필드를 정의하는 설정입니다. 다음과 같이 설정합니다.<br/>
     * <pre>
     *     {@code
     *     filter {
     *          nori {
     *              fields => [
     *                  "field1",
     *                  "field2"
     *              ]
     *          }
     *     }}
     * </pre>
     */
    public static final PluginConfigSpec<List<Object>> FIELDS =
            PluginConfigSpec.arraySetting("fields");

    /**
     * 추출하길 원하는 태그들을 정의하는 설정입니다. 다음과 같이 설정합니다.
     * <pre>
     *     {@code
     *     filter {
     *         nori {
     *             extract_tags => [
     *                  "NR",
     *                  "SP",
     *                  "NNG",
     *                  "..."
     *             ]
     *         }
     *     }
     *     }
     * </pre>
     * 위의 품사 태그들은 <a href="https://docs.google.com/spreadsheets/d/1-9blXKjtjeKZqsf4NzHeYJCrr49-nXeRF6D80udfcwY/edit#gid=589544265">여기</a>에서 확인하실 수 있습니다.
     */
    public static final PluginConfigSpec<List<Object>> EXTRACT_TAGS =
            PluginConfigSpec.arraySetting("extract_tags", new ArrayList<>(), false, false);

    /**
     * 사용자 사전을 정의하는 설정입니다.<br/>
     * 사용자 사전(예: user_dict.txt)이 있는 경로 전체를 문자열로 넣어줍니다.
     * <pre>
     *     {@code
     *     filter {
     *         nori {
     *             user_dictionary_path => "사용자 사전이 정의된 절대경로"
     *         }
     *     }
     *     }
     * </pre>
     */
    public static final PluginConfigSpec<String> USER_DICTIONARY_PATH =
            PluginConfigSpec.stringSetting("user_dictionary_path");

    /**
     * 합성어 처리 방식을 정의하는 설정입니다.<br/>
     * 사용할 수 있는 설정은 다음과 같습니다.
     * <ul>
     *     <li>none : 분해를 하지 않습니다.</li>
     *     <li>discard : 분해한 합성어만 사용합니다.</li>
     *     <li>mixed : 어근과 분해한 합성어 모두 사용합니다.</li>
     * </ul>
     * 위 설정 중에 한 가지를 작성합니다.
     *
     * <pre>
     *     {@code
     *     filter {
     *         nori {
     *             decompound_mode => "none"
     *         }
     *     }}
     * </pre>
     */
    public static final PluginConfigSpec<String> DECOMPOUND_MODE =
            PluginConfigSpec.stringSetting("decompound_mode", "none");

    private final String id;

    private List<String> fields;

    private Analyzer analyzer;
    private final List<POS.Tag> stopTags = Arrays.stream(POS.Tag.values()).collect(Collectors.toList());

    public Nori(String id, Configuration config, Context context) {
        this.id = id;
        initAnalyzer(config);
        initFields(config);
    }


    private void initFields(Configuration config) {
        List<Object> fields = config.get(FIELDS);
        validateFields(fields);
        convertFields(fields);
    }

    private void convertFields(List<Object> fields) {
        this.fields = fields.stream()
                .map(Objects::toString)
                .collect(Collectors.toList());
    }

    private void initAnalyzer(Configuration config) {
        validateExtractTags(config.get(EXTRACT_TAGS));
        initExtractTags(config.get(EXTRACT_TAGS));
        this.analyzer = new KoreanAnalyzer(
                getUserDict(config.get(USER_DICTIONARY_PATH)),
                getDecompoundMode(config.get(DECOMPOUND_MODE)),
                getStopTags(),
                false
        );
    }

    private DecompoundMode getDecompoundMode(String decompoundMode) {
        return DecompoundMode.valueOf(decompoundMode.toUpperCase());
    }

    private UserDictionary getUserDict(String userDictionaryPath) {
        try {
            return StringUtils.isEmpty(userDictionaryPath) ?
                    null :
                    UserDictionary.open(new BufferedReader(new FileReader(userDictionaryPath)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Set<POS.Tag> getStopTags() {
        return new HashSet<>(this.stopTags);
    }

    private void initExtractTags(List<Object> extractTags) {
        extractTags.stream()
                .map(Objects::toString)
                .map(POS::resolveTag)
                .forEach(this.stopTags::remove);
    }

    private void validateExtractTags(List<Object> stopTags) {
        if (stopTags.stream().anyMatch(e -> !(e instanceof String))) {
            throw new RuntimeException("[stop_tags] field's value must be string");
        }
    }

    @Override
    public Collection<Event> filter(Collection<Event> events,
                                    FilterMatchListener matchListener) {
        for (Event event : events) {
            for (String field : fields) {
                process(event, field);
                matchListener.filterMatched(event);
            }
        }
        return events;
    }

    private void process(Event event, String field) {
        if (event.getField(field) == null) {
            event.tag("[" + field + "]'s value is null");
            return;
        }


        if (event.getField(field) instanceof String) {
            String fieldValue = (String) event.getField(field);
            try (TokenStream tokenStream = this.analyzer.tokenStream(field, fieldValue)) {
                CharTermAttribute cta = tokenStream.addAttribute(CharTermAttribute.class);
                tokenStream.reset();
                List<String> morpheme = new ArrayList<>();
                while (tokenStream.incrementToken()) {
                    morpheme.add(cta.toString());
                }
                event.setField(field + "_morpheme", morpheme);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Collection<PluginConfigSpec<?>> configSchema() {
        var pluginConfigSpecs = new ArrayList<PluginConfigSpec<?>>(Collections.singleton(FIELDS));
        pluginConfigSpecs.add(EXTRACT_TAGS);
        pluginConfigSpecs.add(USER_DICTIONARY_PATH);
        return pluginConfigSpecs;
    }

    @Override
    public String getId() {
        return this.id;
    }

    private void validateFields(List<Object> fields) {
        if (fields.stream().anyMatch(e -> !(e instanceof String))) {
            throw new IllegalArgumentException("[plugin_api_keys] must be string");
        }
    }
}
