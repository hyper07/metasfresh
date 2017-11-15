package de.metas.material.event.ddorder;

import static de.metas.material.event.MaterialEventUtils.checkIdGreaterThanZero;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

import de.metas.material.event.commons.ProductDescriptor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/*
 * #%L
 * metasfresh-mrp
 * %%
 * Copyright (C) 2017 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
@Value
@Builder
public class DDOrderLine
{
	int salesOrderLineId;

	@NonNull
	ProductDescriptor productDescriptor;

	@NonNull
	BigDecimal qty;

	/**
	 * {@link DDOrder#getDatePromised()} minus this number of days tells us when the distribution for this particular line needs to start
	 */
	private final int durationDays;

	private final int networkDistributionLineId;

	private final int ddOrderLineId;

	@JsonCreator
	public DDOrderLine(
			@JsonProperty("salesOrderLineId") int salesOrderLineId,
			@JsonProperty("productDescriptor") @NonNull final ProductDescriptor productDescriptor,
			@JsonProperty("qty") @NonNull final BigDecimal qty,
			@JsonProperty("durationDays") final int durationDays,
			@JsonProperty("networkDistributionLineId") final int networkDistributionLineId,
			@JsonProperty("ddOrderLineId") final int ddOrderLineId)
	{
		this.salesOrderLineId = salesOrderLineId;

		productDescriptor.asssertCompleteness();
		this.productDescriptor = productDescriptor;

		this.qty = qty;

		Preconditions.checkArgument(durationDays >= 0, "The Given parameter durationDays=%s needs to be > 0", "durationDays");
		this.durationDays = durationDays;
		this.networkDistributionLineId = checkIdGreaterThanZero("networkDistributionLineId", networkDistributionLineId);

		this.ddOrderLineId = ddOrderLineId;
	}

}
