/*
 * Copyright (c) 2008-2019 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.addon.imap.crypto;

import com.haulmont.addon.imap.config.ImapEncryptionConfig;
import com.haulmont.addon.imap.entity.ImapMailBox;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Base64;

@Component(DefaultEncryptor.NAME)
public class DefaultEncryptor implements Encryptor {
    @SuppressWarnings("WeakerAccess")
    static final String NAME = "imap_DefaultEncryptor";

    private final static Logger log = LoggerFactory.getLogger(DefaultEncryptor.class);

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    private final ImapEncryptionConfig imapConfig;

    private SecretKey secretKey;

    private byte[] iv;

    @SuppressWarnings({"SpringJavaInjectionPointsAutowiringInspection", "CdiInjectionPointsInspection"})
    @Inject
    public DefaultEncryptor(ImapEncryptionConfig imapConfig) {
        this.imapConfig = imapConfig;
    }

    @PostConstruct
    void initKey() {
        if (StringUtils.isBlank(imapConfig.getEncryptionKey())) {
            throw new IllegalStateException(String.format(
                    "Cannot configure encryptor %s, property \"imap.encryption.key\" is not set",
                    getClass().getName()
            ));
        }
        byte[] encryptionKey = Base64.getDecoder().decode(imapConfig.getEncryptionKey());
        secretKey = new SecretKeySpec(encryptionKey, "AES");

        String encryptionIv = imapConfig.getEncryptionIv();
        if (StringUtils.isNotBlank(encryptionIv)) {
            iv = Base64.getDecoder().decode(encryptionIv);
        }

        log.info("Encryptor has been initialised");
    }

    @Override
    public String getEncryptedPassword(ImapMailBox mailBox) {
        if (mailBox.getAuthentication().getPassword() == null) {
            return null;
        }
        log.debug("Encrypt password for {}", mailBox);
        try {
            byte[] encrypted = getCipher(Cipher.ENCRYPT_MODE)
                    .doFinal(saltedPassword(mailBox).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Can't encrypt password for mailbox " + mailBox, e);
        }
    }

    private String saltedPassword(ImapMailBox mailBox) {
        String password = mailBox.getAuthentication().getPassword();
        return RandomStringUtils.random(16) + password;
    }

    @Override
    public String getPlainPassword(ImapMailBox mailBox) {
        if (mailBox.getAuthentication().getPassword() == null) {
            return null;
        }
        log.debug("Decrypt password for {}", mailBox);
        try {
            byte[] password = Base64.getDecoder().decode(mailBox.getAuthentication().getPassword());
            byte[] decrypted = getCipher(Cipher.DECRYPT_MODE).doFinal(password);
            String saltedPassword = new String(decrypted, StandardCharsets.UTF_8);
            return saltedPassword.substring(16);
        } catch (Exception e) {
            throw new RuntimeException("Can't decrypt password for mailbox " + mailBox, e);
        }
    }

    private Cipher getCipher(int mode) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        if (iv != null) {
            cipher.init(mode, secretKey, getAlgorithmParameterSpec());
        } else {
            cipher.init(mode, secretKey);
        }
        return cipher;
    }

    private AlgorithmParameterSpec getAlgorithmParameterSpec() {
        return new IvParameterSpec(iv, 0, iv.length);
    }

}
