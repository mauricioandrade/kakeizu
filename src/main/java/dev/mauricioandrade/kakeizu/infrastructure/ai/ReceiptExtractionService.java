package dev.mauricioandrade.kakeizu.infrastructure.ai;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ImageUrl;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface ReceiptExtractionService {

    // Ollama uses the JSON schema only as an output grammar: the model never sees field descriptions,
    // so what each field means has to be spelled out here.
    @SystemMessage("""
            You read Brazilian payment receipts: NF-e, NFC-e/cupom fiscal, Pix transfer receipts
            and card machine slips (Stone, Cielo, Rede...).
            Return only the requested fields. If a field is not visible, return null. Never guess digits.

            Fields:
            - type: NFE, NFCE, PIX, CARD or UNKNOWN. A card machine slip is CARD, not NFCE
            - amount: total amount paid, digits with dot as decimal separator, e.g. 1234.56
            - date: payment or issue date as yyyy-MM-dd
            - counterparty: who received the money: the invoice issuer, the store name at the top of a card slip,
              or the receiver ("Quem recebeu") of a Pix. Never the payer or the cardholder
            - taxId: CNPJ or CPF of that same counterparty, exactly as printed
            - uniqueId: NF-e access key (44 digits), Pix transaction ID (32 characters starting with E),
              or authorization code (AUT) on a card slip
            - suggestedCategory: FUEL_SUPPLY, GOODS, SERVICES, UTILITIES, PAYROLL or OTHER
            """)
    @UserMessage("Extract the receipt data from this image.")
    ExtractedReceipt extract(@ImageUrl Image image);
}
