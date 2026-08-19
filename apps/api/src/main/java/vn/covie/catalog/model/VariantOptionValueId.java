package vn.covie.catalog.model;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
@Embeddable public class VariantOptionValueId implements Serializable { private UUID variantId; private UUID optionValueId; protected VariantOptionValueId(){} public VariantOptionValueId(UUID variantId,UUID optionValueId){this.variantId=variantId;this.optionValueId=optionValueId;} public UUID getVariantId(){return variantId;} public UUID getOptionValueId(){return optionValueId;} @Override public boolean equals(Object other){return other instanceof VariantOptionValueId value&&java.util.Objects.equals(variantId,value.variantId)&&java.util.Objects.equals(optionValueId,value.optionValueId);}@Override public int hashCode(){return java.util.Objects.hash(variantId,optionValueId);} }
