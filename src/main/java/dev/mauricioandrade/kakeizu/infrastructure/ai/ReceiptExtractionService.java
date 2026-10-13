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
            Return only the value, never the printed label next to it: "2UP8IQ", not "AUT: 2UP8IQ".
            If a label is printed with no value after it, the field is null.

            Fields:
            - type: NFE, NFCE, PIX, CARD or UNKNOWN. A card machine slip is CARD, not NFCE.
              A bank app receipt is PIX only if it says Pix; a card purchase (Crédito, Débito, a card number
              like •••• 1234, a código de autorização) is CARD. Boletos, bill payments and anything else are UNKNOWN
            - amount: the total paid (VALOR TOTAL, VALOR PAGO, the value of the Pix, the amount on the card slip),
              never the price of a single item. Digits with dot as decimal separator, e.g. 1234.56
            - date: payment or issue date as yyyy-MM-dd
            - counterparty: who received the money: the issuer named at the top of an NF-e/NFC-e, the store name on
              a card slip, or the receiver ("Quem recebeu") of a Pix. Never the payer, the consumer (CONSUMIDOR),
              the cardholder, or a card network or machine brand (Stone, Cielo, Mastercard, Visa)
            - taxId: CNPJ or CPF of that same counterparty, exactly as printed
            - uniqueId: NF-e access key (44 digits), Pix transaction ID (32 characters starting with E),
              or authorization code (AUT) on a card slip
            - suggestedCategory: FUEL_SUPPLY, GOODS, SERVICES, UTILITIES, PAYROLL or OTHER
            """)
    @UserMessage("Extract the receipt data from this image.")
    ExtractedReceipt extract(@ImageUrl Image image);
}
