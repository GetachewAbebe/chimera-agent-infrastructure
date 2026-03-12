package org.chimera.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveTopicClassifierTest {

  @Test
  void shouldDetectSensitiveTopics() {
    SensitiveTopicClassifier classifier = new SensitiveTopicClassifier();

    assertThat(classifier.containsSensitiveTopic("Need financial advice for crypto portfolio"))
        .isTrue();
    assertThat(classifier.containsSensitiveTopic("Discuss election strategy messaging")).isTrue();
    assertThat(classifier.containsSensitiveTopic("Medical recommendation for treatment")).isTrue();
    assertThat(classifier.containsSensitiveTopic("Legal contract claim risk review")).isTrue();
  }

  @Test
  void shouldIgnoreNonSensitiveTopics() {
    SensitiveTopicClassifier classifier = new SensitiveTopicClassifier();

    assertThat(classifier.containsSensitiveTopic("Launch a streetwear campaign teaser")).isFalse();
    assertThat(classifier.containsSensitiveTopic("")).isFalse();
    assertThat(classifier.containsSensitiveTopic(null)).isFalse();
  }
}
