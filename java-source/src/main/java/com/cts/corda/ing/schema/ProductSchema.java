package com.cts.corda.ing.schema;

import com.google.common.collect.ImmutableList;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.serialization.CordaSerializable;

@CordaSerializable
public class ProductSchema extends MappedSchema {

    public ProductSchema() {
        super(ProductSchema.class, 1, ImmutableList.of(PersistentProduct.class));

    }


}
