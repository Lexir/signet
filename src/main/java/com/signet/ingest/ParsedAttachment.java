package com.signet.ingest;

/**
 * Разобранное вложение письма.
 *
 * @param filename    имя файла
 * @param contentType MIME-тип (без параметров)
 * @param data        байты содержимого
 */
public record ParsedAttachment(String filename, String contentType, byte[] data) {
}
