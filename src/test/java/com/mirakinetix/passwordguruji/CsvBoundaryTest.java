package com.mirakinetix.passwordguruji;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CsvBoundaryTest {
    @TempDir Path directory;

    @Test void roundTripsMaximumVaultFieldAfterEncoding() throws IOException {
        Path file = directory.resolve("large.csv");
        String value = "東京".repeat(333_333) + "ok";
        VaultEntry record = VaultEntry.create("=literal","",value,"","end\r","Login","",false,
                List.of(),Map.of("Unicode",value));
        VaultCsv.exportFile(file,List.of(record));
        VaultEntry restored = VaultCsv.importFile(file).getFirst();
        assertEquals(record.title(),restored.title());
        assertEquals(value,restored.password());
        assertEquals(record.notes(),restored.notes());
        assertEquals(record.customFields(),restored.customFields());
    }

    @Test void cellLimitMatchesForImportAndExportWithoutReplacingExistingFile() throws IOException {
        Path file = directory.resolve("boundary.csv");
        String exact = "x".repeat(8 * 1024 * 1024 - "PGTEXT:".length());
        VaultCsv.exportFile(file,List.of(VaultEntry.create(exact,"","","","","Login")));
        assertEquals(exact,VaultCsv.importFile(file).getFirst().title());
        byte[] prior = Files.readAllBytes(file);
        assertThrows(IOException.class, () -> VaultCsv.exportFile(file,
                List.of(VaultEntry.create(exact+"x","","","","","Login"))));
        assertArrayEquals(prior,Files.readAllBytes(file));
        Path oversized = directory.resolve("oversized.csv");
        Files.writeString(oversized,"x".repeat(8 * 1024 * 1024 + 1));
        assertThrows(IOException.class, () -> VaultCsv.importFile(oversized));
    }
}
