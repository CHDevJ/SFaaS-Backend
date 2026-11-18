package com.sfaasproject.stf.equipment.service;

import com.sfaasproject.stf.equipment.service.EquipmentPhotoValidationService.Format;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 설비 사진을 서버 폴더(app.equipment.photo-dir)에 저장한다. 파일 이름은 추측할 수 없는
 * 128비트 난수이고 DB 에는 이 이름만 남긴다. 트랜잭션 안에서 호출하면 롤백될 때 새 파일을 지우고,
 * 교체·삭제된 옛 파일은 커밋된 뒤에 지운다.
 */
@Component
public class EquipmentPhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(EquipmentPhotoStorage.class);
    private static final Pattern FILE_NAME = Pattern.compile("[0-9a-f]{32}\\.(jpg|png|webp)");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path root;

    public EquipmentPhotoStorage(@Value("${app.equipment.photo-dir:./uploads/equipment-photos}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    /** 검증을 마친 사진을 저장하고 파일 이름을 돌려준다. */
    public String save(MultipartFile photo, Format format) {
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);
        String name = HexFormat.of().formatHex(random) + "." + extension(format);
        Path target = root.resolve(name);
        try {
            Files.createDirectories(root);
            Path temp = Files.createTempFile(root, "upload-", ".tmp");
            try (InputStream in = photo.getInputStream()) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to store equipment photo", ex);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        deleteQuietly(name);
                    }
                }
            });
        }
        return name;
    }

    /** 더 쓰지 않는 사진을 지운다. 트랜잭션 안이면 커밋된 뒤에 지운다. */
    public void deleteAfterCommit(String name) {
        if (name == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(name);
                }
            });
        } else {
            deleteQuietly(name);
        }
    }

    private static String extension(Format format) {
        return switch (format) {
            case JPEG -> "jpg";
            case PNG -> "png";
            case WEBP -> "webp";
        };
    }

    /** 저장된 사진 파일. 이름 형식이 맞지 않으면(경로 조작 포함) 없는 것으로 본다. */
    public Optional<Path> find(String name) {
        if (!isValidName(name)) {
            return Optional.empty();
        }
        Path path = root.resolve(name);
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    public static boolean isValidName(String name) {
        return name != null && FILE_NAME.matcher(name).matches();
    }

    private void deleteQuietly(String name) {
        if (!isValidName(name)) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(name));
        } catch (IOException ex) {
            log.warn("Failed to delete equipment photo file {}", name, ex);
        }
    }
}
