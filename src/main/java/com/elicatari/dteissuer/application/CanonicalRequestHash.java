package com.elicatari.dteissuer.application;

import com.elicatari.dteissuer.application.port.in.IssueDteCommand;
import com.elicatari.dteissuer.domain.DocumentType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hash del pedido canonicalizado (tipo, RUT canónico, neto), no del JSON crudo.
 */
final class CanonicalRequestHash {

    private CanonicalRequestHash() {}

    static String of(IssueDteCommand command) {
        String canonical = DocumentType.BOLETA_39.siiCode()
                + "\n"
                + command.rut().value()
                + "\n"
                + command.neto().amount().toPlainString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}