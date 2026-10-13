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
            - time: time of that same payment or issue as HH:mm (24 hours). Date and time are often printed together
              at the top, e.g. "12/08/2022 • 14:05" is date 2022-08-12 and time 14:05
            - counterparty: who received the money: the issuer named at the top of an NF-e/NFC-e, the store name on
              a card slip, or the receiver ("Quem recebeu") of a Pix. Never the payer, the consumer (CONSUMIDOR),
              the cardholder, or a card network or machine brand (Stone, Cielo, Mastercard, Visa)
            - taxId: CNPJ or CPF of that same counterparty, exactly as printed
            - uniqueId: NF-e access key (44 digits), Pix transaction ID (32 characters starting with E),
              or authorization code (AUT) on a card slip
            - suggestedCategory: FUEL_SUPPLY, GOODS, SERVICES, UTILITIES, PAYROLL or OTHER
            - paymentMethod: CASH, CREDIT, DEBIT, PIX or OTHER. On an NF-e/NFC-e, read "Forma de pagamento"
              (Dinheiro is CASH, Cartão de Crédito is CREDIT, Cartão de Débito is DEBIT)
            - installments: number of installments ("5X SEM JUROS" is 5, "parcelado em 3" is 3); 1 if paid at once
              ("à vista")
            - cardBrand: VISA, MASTERCARD, ELO, AMEX, HIPERCARD or OTHER, only for card payments
            """)
    @UserMessage("Extract the receipt data from this image.")
    ExtractedReceipt extract(@ImageUrl Image image);
}
