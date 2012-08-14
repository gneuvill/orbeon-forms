/**
 * Copyright (C) 2012 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util

import java.security.{SecureRandom, MessageDigest}
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.{IvParameterSpec, PBEKeySpec, SecretKeySpec}
import org.orbeon.oxf.xforms.XFormsProperties
import org.apache.commons.pool.BasePoolableObjectFactory

object SecureUtils {

    private val HexDigits = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

    // Modern algorithms as of 2012
    private val KeyCipherAlgorithm = "PBKDF2WithHmacSHA1"
    private val EncryptionCipherTransformation = "AES/CBC/PKCS5Padding"

    val AESBlockSize = 128
    val AESIVSize    = AESBlockSize / 8

    // Secret key valid for the life of the classloader
    private lazy val secretKey: SecretKey = {

        val password  = XFormsProperties.getXFormsPassword
        val keyLength = XFormsProperties.getXFormsKeyLength

        // Random seeded salt
        val salt = new Array[Byte](8)
        (new SecureRandom).nextBytes(salt)

        val spec = new PBEKeySpec(password.toCharArray, salt, 65536, keyLength)

        val factory = SecretKeyFactory.getInstance(KeyCipherAlgorithm)
        new SecretKeySpec(factory.generateSecret(spec).getEncoded, "AES")
    }

    // Cipher is not thread-safe, see:
        // http://stackoverflow.com/questions/6957406/is-cipher-thread-safe
    private val pool = new SoftReferenceObjectPool(new BasePoolableObjectFactory[Cipher] {
        def makeObject() = Cipher.getInstance(EncryptionCipherTransformation)
    })

    private def withCipher[T](body: Cipher ⇒ T) = {
        val cipher = pool.borrowObject()
        try body(cipher)
        finally pool.returnObject(cipher)
    }

    // Encrypt a byte array using the given password
    // The result is converted to Base64 encoding without line breaks or spaces
    def encrypt(password: String, bytes: Array[Byte]): String = encryptIV(bytes, None)

    def encryptIV(bytes: Array[Byte], ivOption: Option[Array[Byte]]): String =
        withCipher { cipher ⇒
            ivOption match {
                case Some(iv) ⇒
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv))
                    // Don't prepend IV
                    Base64.encode(cipher.doFinal(bytes), false)
                case None ⇒
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    val params = cipher.getParameters
                    val iv = params.getParameterSpec(classOf[IvParameterSpec]).getIV
                    // Prepend the IV to the ciphertext
                    Base64.encode(iv ++ cipher.doFinal(bytes), false)
            }
        }

    // Decrypt a Base64-encoded string into a byte array using the given password.
    def decrypt(password: String, text: String): Array[Byte] = decryptIV(text, None)

    def decryptIV(text: String, ivOption: Option[Array[Byte]]): Array[Byte] =
        withCipher { cipher ⇒
            val (iv, message) =
                ivOption match {
                    case Some(iv) ⇒
                        // The IV was passed
                        (iv, Base64.decode(text))
                    case None ⇒
                        // The IV was prepended to the message
                        Base64.decode(text).splitAt(AESIVSize)
                }

            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv))
            cipher.doFinal(message)
        }

    def digestString(data: String, algorithm: String, encoding: String): String =
        digestBytes(data.getBytes("utf-8"), algorithm, encoding)

    private def withEncoding(bytes: Array[Byte], encoding: String) = encoding match {
        case "base64" ⇒ Base64.encode(bytes, false)
        case "hex"    ⇒ byteArrayToHex(bytes)
        case _        ⇒ throw new IllegalArgumentException("Invalid digest encoding (must be one of 'base64' or 'hex'): " + encoding)
    }

    def digestBytes(data: Array[Byte], algorithm: String, encoding: String): String = {
        val messageDigest = MessageDigest.getInstance(algorithm)
        messageDigest.update(data)
        withEncoding(messageDigest.digest, encoding)
    }

    def hmacString(key: String, data: String, algorithm: String, encoding: String): String =
        hmacBytes(key.getBytes("utf-8"), data.getBytes("utf-8"), algorithm, encoding)

    def hmacBytes(key: Array[Byte], data: Array[Byte], algorithm: String, encoding: String): String = {
        val secretKey = new SecretKeySpec(key, algorithm)
        val mac = Mac.getInstance("Hmac" + algorithm.toUpperCase.replace("-", ""))
        mac.init(secretKey)

        val digestBytes = mac.doFinal(data)
        val result = withEncoding(digestBytes, encoding)

        result.replace("\n", "")
    }

    def byteArrayToHex(bytes: Array[Byte]): String = {
        val sb = new StringBuilder(bytes.length * 2)

        var i: Int = 0
        while (i < bytes.length) {
            sb.append(HexDigits((bytes(i) >> 4) & 0xf))
            sb.append(HexDigits(bytes(i) & 0xf))
            i += 1
        }

        sb.toString
    }
}