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

package im.turms.service.logging;

import im.turms.common.model.dto.notification.TurmsNotification;
import im.turms.common.model.dto.request.TurmsRequest;
import im.turms.server.common.util.ByteBufUtil;
import im.turms.server.common.util.Formatter;
import io.netty.buffer.ByteBuf;

import static im.turms.server.common.logging.CommonLogger.LOG_FIELD_DELIMITER;
import static im.turms.server.common.logging.CommonLogger.NOTIFICATION_LOGGER;

/**
 * @author James Chen
 * @implNote Don't use StringBuilder because String#join is more efficient
 */
public final class NotificationLogging {

    private NotificationLogging() {
    }

    /**
     * Note that although TurmsNotification can represent a "response" or "notification",
     * the method is only designed to log "notification" instead of "response"
     */
    public static void log(boolean sent, TurmsNotification notification, int recipientCount) {
        TurmsRequest relayedRequest = notification.getRelayedRequest();
        ByteBuf buffer = ByteBufUtil.join(64, LOG_FIELD_DELIMITER,
                // User info
                Formatter.toCharBytes(notification.getRequesterId()),
                // Notification info
                sent ? "SENT" : "UNSENT",
                Formatter.toCharBytes(recipientCount),
                notification.hasCloseStatus() ? Formatter.toCharBytes(notification.getCloseStatus()) : null,
                Formatter.toCharBytes(notification.getSerializedSize()),
                // Relayed request info
                Formatter.toCharBytes(relayedRequest.getRequestId()),
                relayedRequest.getKindCase().name());
        NOTIFICATION_LOGGER.info(buffer);
    }

}