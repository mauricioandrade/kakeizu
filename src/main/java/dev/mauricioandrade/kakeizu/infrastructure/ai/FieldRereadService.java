package dev.mauricioandrade.kakeizu.infrastructure.ai;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ImageUrl;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Asks for one field that failed its check digits. The whole image goes again: a small model gives no reliable
 * coordinates to crop the field from. No chat memory, so a second field read on the same receipt does not carry
 * the first one (and its image) along.
 */
@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface FieldRereadService {

    @SystemMessage("""
            You read one field of a Brazilian payment receipt, exactly as printed. Never guess digits.
            """)
    @UserMessage("""
            Read ONLY the {fieldName} from this receipt.
            It has exactly {length} characters. Read it character by character, left to right.
            Return only those characters, no spaces or punctuation. If any character is unreadable, return null.
            """)
    RereadField reread(@ImageUrl Image image, String fieldName, int length);
}
