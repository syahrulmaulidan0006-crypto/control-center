package com.example.controlcenter;

import java.util.ArrayList;
import java.util.List;

/** Menyusun tombol berbagai ukuran ke kisi: tiap tombol mengisi tempat kosong pertama. */
final class Pack {
    private final int columns, maxRows;
    private final List<boolean[]> rows = new ArrayList<>();

    /** maxRows 0 berarti tanpa batas. */
    Pack(int columns, int maxRows) {
        this.columns = columns;
        this.maxRows = maxRows;
    }

    /** Mengembalikan {kolom, baris}, atau null bila tidak muat. */
    int[] place(int w, int h) {
        if (w > columns || maxRows > 0 && h > maxRows) return null;
        for (int row = 0; maxRows == 0 || row + h <= maxRows; row++) {
            for (int column = 0; column + w <= columns; column++) {
                if (!free(column, row, w, h)) continue;
                for (int y = row; y < row + h; y++) {
                    while (rows.size() <= y) rows.add(new boolean[columns]);
                    for (int x = column; x < column + w; x++) rows.get(y)[x] = true;
                }
                return new int[]{column, row};
            }
        }
        return null;
    }

    private boolean free(int column, int row, int w, int h) {
        for (int y = row; y < row + h && y < rows.size(); y++) {
            for (int x = column; x < column + w; x++) {
                if (rows.get(y)[x]) return false;
            }
        }
        return true;
    }
}
