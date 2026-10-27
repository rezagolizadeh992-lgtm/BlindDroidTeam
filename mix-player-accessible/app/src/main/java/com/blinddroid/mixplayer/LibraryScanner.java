package com.blinddroid.mixplayer;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class LibraryScanner {
    private static final String[] COLUMNS = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };
    private static final List<String> AUDIO_EXTENSIONS = Arrays.asList(
            ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".flac", ".wav", ".wma", ".3gp", ".amr", ".mid", ".midi"
    );

    private LibraryScanner() { }

    static List<AudioTrack> scan(ContentResolver resolver, Uri treeUri) {
        ArrayList<AudioTrack> tracks = new ArrayList<>();
        HashSet<String> visited = new HashSet<>();
        try {
            String rootId = DocumentsContract.getTreeDocumentId(treeUri);
            walk(resolver, treeUri, rootId, visited, tracks, 0);
        } catch (Exception ignored) { }
        return tracks;
    }

    private static void walk(ContentResolver resolver, Uri treeUri, String parentId,
                             Set<String> visited, List<AudioTrack> output, int depth) {
        if (depth > 20 || !visited.add(parentId)) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
        try (Cursor cursor = resolver.query(children, COLUMNS, null, null, null)) {
            if (cursor == null) return;
            int idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int typeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int modifiedCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
            while (cursor.moveToNext()) {
                String id = idCol >= 0 ? cursor.getString(idCol) : null;
                String name = nameCol >= 0 ? cursor.getString(nameCol) : "";
                String mime = typeCol >= 0 ? cursor.getString(typeCol) : null;
                if (id == null) continue;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    walk(resolver, treeUri, id, visited, output, depth + 1);
                } else if (isAudio(mime, name)) {
                    Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                    long modified = modifiedCol >= 0 && !cursor.isNull(modifiedCol) ? cursor.getLong(modifiedCol) : 0;
                    output.add(new AudioTrack(fileUri, stripExtension(name), "خواننده ناشناس", 0, modified));
                }
            }
        } catch (Exception ignored) { }
    }

    private static boolean isAudio(String mime, String name) {
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("audio/")) return true;
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (String extension : AUDIO_EXTENSIONS) if (lower.endsWith(extension)) return true;
        return false;
    }

    private static String stripExtension(String name) {
        if (name == null || name.trim().isEmpty()) return "بدون نام";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
