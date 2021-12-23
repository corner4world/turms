/*
 * Copyright (C) 2019 The Turms Project
 * https://github.com/turms-im/turms
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.turms.plugin.antispam;

import im.turms.common.constant.DeviceType;
import im.turms.common.model.dto.request.TurmsRequest;
import im.turms.common.model.dto.request.group.CreateGroupRequest;
import im.turms.plugin.antispam.ac.Store;
import im.turms.plugin.antispam.property.AntiSpamProperties;
import im.turms.plugin.antispam.property.TextParsingStrategy;
import im.turms.plugin.antispam.property.UnwantedWordHandleStrategy;
import im.turms.server.common.constant.TurmsStatusCode;
import im.turms.server.common.exception.TurmsBusinessException;
import im.turms.service.workflow.access.servicerequest.dto.ClientRequest;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author James Chen
 */
class AntiSpamHandlerTests {

    Path path = Path.of("./anti-spam-handler-tests.tmp");

    @Test
    void shouldRejectRequest() {
        AntiSpamHandler handler = createHandler(UnwantedWordHandleStrategy.REJECT_REQUEST,
                TextParsingStrategy.NORMALIZATION_TRANSLITERATION, false);
        TurmsRequest.Builder builder = TurmsRequest
                .newBuilder()
                .setCreateGroupRequest(CreateGroupRequest.newBuilder()
                        .setName(new String(Store.UNWANTED_TERMS.get(0)))
                        .build());
        ClientRequest clientRequest = new ClientRequest(1L, DeviceType.DESKTOP, 1L, builder, null);
        Mono<ClientRequest> result = handler.transform(clientRequest);
        StepVerifier.create(result)
                .expectErrorSatisfies(t -> {
                    assertThat(t).isInstanceOf(TurmsBusinessException.class);
                    assertThat(((TurmsBusinessException) t).getCode()).isEqualTo(TurmsStatusCode.MESSAGE_IS_ILLEGAL);
                })
                .verify();
    }

    @Test
    void shouldMask_forLatin1Text() {
        String original = "Oh no, loving you is not right. But no, don't take me home tonight. Oh yes, so baby won't you hold me tight";
        String expected = "*****, ***********************. ******, don't take me home tonight. ******, so baby won't you hold me tight";
        testMask(original, expected, TextParsingStrategy.NORMALIZATION);
    }

    @Test
    void shouldMask_forUTF16TextWithAscii() {
        String original = "Hello敏感词句.,asd#(&𤳵/()12%&123敏gan词321";
        String expected = "Hello****.,asd#(&𤳵/()12%&********321";
        testMask(original, expected, TextParsingStrategy.NORMALIZATION_TRANSLITERATION);
    }

    @Test
    void shouldMask_forUTF16TextWithoutAscii() {
        String original = "薬指のリングより　人目忍ぶ恋選んだ　強い女に見えても　心の中いつも　切なさに　揺れてる";
        String expected = "**の***より　********　***に***も　*の*いつも　***に　***る";
        testMask(original, expected, TextParsingStrategy.NORMALIZATION);
    }

    @Test
    void shouldReturnUnwantedWord_forLatin1Text() {
        String original = "Oh no, loving you is not right. But no, don't take me home tonight. Oh yes, so baby won't you hold me tight";
        List<String> words = List.of(
                "Oh no",
                "loving you is not right",
                "But no",
                "Oh yes");
        testReturnUnwantedWords(original, words, TextParsingStrategy.NORMALIZATION);
    }

    @Test
    void shouldReturnUnwantedWord_forUTF16TextWithAscii() {
        String original = "Hello敏感词句.,asd#(&𤳵/()12%&123敏gan词321";
        List<String> words = List.of(
                "敏感词",
                "敏感词句",
                "123",
                "敏gan词");
        testReturnUnwantedWords(original, words, TextParsingStrategy.NORMALIZATION_TRANSLITERATION);
    }

    @Test
    void shouldReturnUnwantedWord_forUTF16TextWithoutAscii() {
        String original = "薬指のリングより　人目忍ぶ恋選んだ　強い女に見えても　心の中いつも　切なさに　揺れてる";
        List<String> words = List.of(
                "薬指",
                "リング",
                "人目忍ぶ恋",
                "選んだ",
                "強い",
                "女",
                "見えて",
                "心",
                "中",
                "切なさ",
                "揺れて");
        testReturnUnwantedWords(original, words, TextParsingStrategy.NORMALIZATION);
    }

    void testMask(String original, String expected, TextParsingStrategy strategy) {
        AntiSpamHandler handler = createHandler(UnwantedWordHandleStrategy.MASK_TEXT, strategy, false);
        TurmsRequest.Builder builder = TurmsRequest
                .newBuilder()
                .setCreateGroupRequest(CreateGroupRequest.newBuilder()
                        .setName(original));
        ClientRequest clientRequest = new ClientRequest(1L, DeviceType.DESKTOP, 1L, builder, null);
        Mono<ClientRequest> result = handler.transform(clientRequest);
        StepVerifier.create(result)
                .expectNextMatches(request -> {
                    CreateGroupRequest createGroupRequest = request.turmsRequest().getCreateGroupRequest();
                    assertThat(createGroupRequest.getName()).isEqualTo(expected);
                    return true;
                })
                .verifyComplete();
    }

    void testReturnUnwantedWords(String original, List<String> words, TextParsingStrategy strategy) {
        AntiSpamHandler handler = createHandler(UnwantedWordHandleStrategy.REJECT_REQUEST, strategy, true);
        TurmsRequest.Builder builder = TurmsRequest
                .newBuilder()
                .setCreateGroupRequest(CreateGroupRequest.newBuilder()
                        .setName(original));
        ClientRequest clientRequest = new ClientRequest(1L, DeviceType.DESKTOP, 1L, builder, null);
        Mono<ClientRequest> result = handler.transform(clientRequest);
        StepVerifier.create(result)
                .expectErrorMatches(throwable -> {
                    TurmsBusinessException e = (TurmsBusinessException) throwable;
                    assertThat(e.getCode()).isEqualTo(TurmsStatusCode.MESSAGE_IS_ILLEGAL);
                    String expected = String.join(String.valueOf((char) SpamDetector.UNWANTED_WORD_DELIMITER), words);
                    assertThat(e.getReason()).isEqualTo(expected);
                    return true;
                })
                .verify();
    }

    @SneakyThrows
    AntiSpamHandler createHandler(UnwantedWordHandleStrategy handleStrategy, TextParsingStrategy strategy, boolean shouldReturnUnwantedWords) {
        try {
            List<String> terms = Store.UNWANTED_TERMS.stream().map(String::new).toList();
            String text = String.join("\n", terms);
            Files.writeString(path, text, StandardCharsets.UTF_8);
            AntiSpamProperties properties = new AntiSpamProperties()
                    .toBuilder()
                    .textParsingStrategy(strategy)
                    .unwantedWordHandleStrategy(handleStrategy)
                    .maxNumberOfUnwantedWordsToReturn(shouldReturnUnwantedWords ? Integer.MAX_VALUE : 0)
                    .build();
            properties.getDictParsing().setTextFilePath(path.toString());
            return new AntiSpamHandler(properties);
        } finally {
            Files.delete(path);
        }
    }

}
