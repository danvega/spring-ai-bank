package dev.danvega.bank.router;

// The descriptions are the routing rules.
public enum ModelTier {

    LUNA("gpt-6-luna", """
            A general question with a short, standard answer that is the same for every customer. \
            Card and account FAQs: how to freeze or unfreeze a card, change a PIN, check a balance, \
            ATM limits, branch hours. Nothing about this customer's own transactions, and no money at risk."""),

    SOL("gpt-6.1-sol", """
            A question about this customer's own transactions or disputes that takes a few steps to answer. \
            A charge they don't recognize, a duplicate charge, a refund that hasn't arrived, \
            the status of a dispute, comparing several charges. Routine, but the answer depends on their data."""),

    ASTRA("gpt-6-astra", """
            A high-stakes or complex question, where a wrong answer can hurt the customer. \
            Financial hardship, missed payments, debt, mortgages and loans, fraud or identity theft, \
            a bereavement, a formal complaint, or a request with several hard parts.""");

    private final String modelId;
    private final String description;

    ModelTier(String modelId, String description) {
        this.modelId = modelId;
        this.description = description;
    }

    public String modelId() {
        return modelId;
    }

    public String description() {
        return description;
    }

    public ModelTier stronger() {
        return this == ASTRA ? ASTRA : values()[ordinal() + 1];
    }

}
