package dk.nykredit.bank.account.exposure.rs.model;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * The necessary input for creation of a Transaction.
 */
@ApiModel(value = "TransactionUpdate",
        description = "the input necessary for creating a transaction")
public class TransactionUpdateRepresentation {

    @NotNull
    @Pattern(regexp = ".{1,256}")
    private String description;

    @NotNull
    @Pattern(regexp = "^([0-9]{1,9})((\\.)([0-9]{2}))?")
    private String amount;


    @ApiModelProperty(
            access = "public",
            name = "description",
            required = true,
            example = "Starbucks Coffee",
            notes = "the human readable description of the transaction.",
            value = "Readable and Writeable")
    public String getDescription() {
        return description;
    }

    @ApiModelProperty(
            access = "public",
            name = "amount",
            required = true,
            example = "123.45",
            notes = "the amount - in this example without currency.",
            value = "Readable and Writeable")
    public String getAmount() {
        return amount;
    }
}
