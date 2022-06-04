# Logstash Filter Nori [![Build Status](https://app.travis-ci.com/twosom/logstash-filter-korean-number.svg?branch=main)](https://app.travis-ci.com/twosom/logstash-filter-korean-number)

이 플러그인은 [Logstash](https://github.com/elastic/logstash) 를 위한 필터 플러그인으로

Apache [Lucene](https://github.com/apache/lucene) 프로젝트의 Nori 분석기를 기반으로 작성되었습니다.

## 사용방법

### Logstash 버전이 8.2.2 이상인 경우

Logstash 버전이 8.2.2인 경우 해당 플러그인의 gem 파일을 직접 받지 않고도 설치할 수 있습니다.

Logstash 디렉토리로 이동 후

```shell
./bin/logstash-plugin install --no-verify logstash-filter-nori
```

명령어로 설치합니다.

---

### Logstash 버전이 8.2.2 미만인 경우

Logstash 버전이 8.2.2 버전 미만인 경우에는 해당 플러그인의 gem 파일을 이용해서 설치해야 합니다.

gem 파일은 직접 빌드하거나 release 탭에서 내려받으실 수 있습니다.

이 플러그인을 직접 빌드하기 위해서는 맨 처음에 `logstash-core` 라이브러리가 필요합니다.

1

```shell
git clone https://github.com/elastic/logstash.git
```

2

``` shell
cd ./logstash
```

3

``` shell
./gradlew clean assemble
```

4

``` shell
export LOGSTASH_CORE_PATH=$PWD/logstash-core
```

5

``` shell
cd ../
```

6

``` shell
git clone https://github.com/twosom/logstash-filter-nori
```

7

``` shell
echo "LOGSTASH_CORE_PATH=$LOGSTASH_CORE_PATH" >> gradle.properties
```

8

``` shell
./gradlew clean gem
```

9

``` shell
export FILTER_NORI_PATH=$PWD/logstash-filter-korean_jamo-현재 필터 노리 플러그인 버전.gem  
```

로그스태시가 설치 된 폴더로 이동 후

``` shell
./bin/logstash-plugin install $FILTER_NORI_PATH 
```

---

### 필터 설정

```sh

filter {
  
  nori {
    fields => [                                 # Logstash Filter Nori 를 적용할 필드 이름을 배열로 설정합니다. 필터가 적용된 값들은 필드이름_morpheme 이라는 필드에 저장됩니다.
      "field1",
      "field2"
    ]
    
    extract_tags => [                           # 추출하길 원하는 품사 태그들을 배열로 설정합니다. 아무것도 설정하지 않으면 필드이름_morpheme 에는 아무 값도 들어가지 않습니다.
      "NNP",
      "NNG",
      ...
    ]
    
    user_dictionary_path => "사용자 사전의 절대경로"  # 사용자 사전이 저장된 절대경로를 문자열로 설정합니다. 필수값은 아닙니다. 
    
  }
  
}
```

**품사 태그들은 [이곳](https://docs.google.com/spreadsheets/d/1-9blXKjtjeKZqsf4NzHeYJCrr49-nXeRF6D80udfcwY/edit#gid=589544265)에서
확인하실 수 있습니다.**

### 사용 예제

#### input

```bash
bin/logstash -e "input { generator {'message' => '안녕하세요. 잘 부탁 드려요.'} } filter { nori { fields => ["message"] extract_tags => ["NNP", "NNG"] } }  output { stdout{} }"
```

#### output

```shell
{
  "event" => {
      "original" => "안녕하세요. 잘 부탁 드려요.",
      "sequence" => 4999
  },
  "@timestamp" => 2022-06-03T17:57:51.623386Z,
  "message" => "안녕하세요. 잘 부탁 드려요.",
  "host" => {
    "name" => "hopeui-MacBookPro.local"
  },
  "message_morpheme" => [
    [0] "안녕",
    [1] "부탁"
  ],
  "@version" => "1"
}
```
