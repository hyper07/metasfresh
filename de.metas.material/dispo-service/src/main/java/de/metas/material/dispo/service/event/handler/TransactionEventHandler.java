package de.metas.material.dispo.service.event.handler;

import java.math.BigDecimal;
import java.util.Collection;

import org.adempiere.exceptions.AdempiereException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import de.metas.Profiles;
import de.metas.material.dispo.commons.candidate.Candidate;
import de.metas.material.dispo.commons.candidate.Candidate.CandidateBuilder;
import de.metas.material.dispo.commons.candidate.CandidateType;
import de.metas.material.dispo.commons.candidate.DemandDetail;
import de.metas.material.dispo.commons.candidate.DistributionDetail;
import de.metas.material.dispo.commons.candidate.ProductionDetail;
import de.metas.material.dispo.commons.candidate.TransactionDetail;
import de.metas.material.dispo.commons.repository.CandidateRepositoryRetrieval;
import de.metas.material.dispo.commons.repository.CandidatesQuery;
import de.metas.material.dispo.commons.repository.MaterialDescriptorQuery;
import de.metas.material.dispo.service.candidatechange.CandidateChangeService;
import de.metas.material.event.MaterialEventHandler;
import de.metas.material.event.transactions.AbstractTransactionEvent;
import de.metas.material.event.transactions.TransactionCreatedEvent;
import de.metas.material.event.transactions.TransactionDeletedEvent;
import lombok.NonNull;

/*
 * #%L
 * metasfresh-material-dispo
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
@Service
@Profile(Profiles.PROFILE_MaterialDispo)
public class TransactionEventHandler implements MaterialEventHandler<AbstractTransactionEvent>
{
	private final CandidateChangeService candidateChangeHandler;
	private final CandidateRepositoryRetrieval candidateRepository;

	public TransactionEventHandler(
			@NonNull final CandidateChangeService candidateChangeHandler,
			@NonNull final CandidateRepositoryRetrieval candidateRepository)
	{
		this.candidateChangeHandler = candidateChangeHandler;
		this.candidateRepository = candidateRepository;
	}

	@Override
	public Collection<Class<? extends AbstractTransactionEvent>> getHandeledEventType()
	{
		return ImmutableList.of(TransactionCreatedEvent.class, TransactionDeletedEvent.class);
	}

	@Override
	public void handleEvent(@NonNull final AbstractTransactionEvent event)
	{
		final Candidate candidate = createCandidateForTransactionEvent(event);
		candidateChangeHandler.onCandidateNewOrChange(candidate);
	}

	@VisibleForTesting
	Candidate createCandidateForTransactionEvent(@NonNull final AbstractTransactionEvent event)
	{
		final Candidate candidate;
		if (event.getShipmentScheduleId() > 0)
		{
			candidate = prepareCandidateForShipmentScheduleId(event);
		}
		else if (event.getPpOrderId() > 0)
		{
			candidate = prepareCandidateForPPorder(event);
		}
		else if(event.getDdOrderLineId() > 0)
		{
			candidate = prepareCandidateForDDorder(event);
		}
		else
		{
			candidate = prepareUnrelatedCandidate(event);
		}
		return candidate;
	}

	private Candidate prepareCandidateForShipmentScheduleId(@NonNull final AbstractTransactionEvent event)
	{
		final DemandDetail demandDetail = DemandDetail.forShipmentScheduleIdAndOrderLineId(event.getShipmentScheduleId(), -1);

		final CandidatesQuery query = CandidatesQuery.builder().type(CandidateType.DEMAND)
				.demandDetail(demandDetail) // only search via demand detail, ..the product and warehouse will also match, but e.g. the date probably won't!
				.build();

		final Candidate existingCandidate = candidateRepository.retrieveLatestMatchOrNull(query);
		final Candidate candidate;

		final boolean unrelatedNewTransaction = existingCandidate == null && event instanceof TransactionCreatedEvent;
		if (unrelatedNewTransaction)
		{
			candidate = createBuilderForNewUnrelatedCandidate((TransactionCreatedEvent)event)
					.demandDetail(demandDetail)
					.transactionDetail(createTransactionDetail(event))
					.build();
		}
		else if (existingCandidate != null)
		{
			candidate = newCandidateWithAddedTransactionDetail(
					existingCandidate,
					createTransactionDetail(event));
		}
		else
		{
			throw createExceptionForUnexpectedEvent(event);
		}
		return candidate;
	}

	private Candidate prepareCandidateForPPorder(@NonNull final AbstractTransactionEvent event)
	{
		final Candidate candidate;
		final TransactionDetail transactionDetailOfEvent = createTransactionDetail(event);

		final ProductionDetail productionDetail = ProductionDetail.builder()
				.ppOrderId(event.getPpOrderId())
				.ppOrderLineId(event.getPpOrderLineId())
				.actualQty(event.getQuantity())
				.build();

		final CandidatesQuery query = CandidatesQuery.builder()
				.productionDetail(productionDetail) // only search via production detail, ..the product and warehouse will also match, but e.g. the date probably won't!
				.build();

		final Candidate existingCandidate = candidateRepository.retrieveLatestMatchOrNull(query);

		final boolean unrelatedNewTransaction = existingCandidate == null && event instanceof TransactionCreatedEvent;
		if (unrelatedNewTransaction)
		{
			candidate = createBuilderForNewUnrelatedCandidate((TransactionCreatedEvent)event)
					.productionDetail(productionDetail)
					.transactionDetail(transactionDetailOfEvent)
					.build();
		}
		else if (existingCandidate != null)
		{
			candidate = newCandidateWithAddedTransactionDetail(
					existingCandidate,
					transactionDetailOfEvent);
		}
		else
		{
			throw createExceptionForUnexpectedEvent(event);
		}
		return candidate;
	}

	private Candidate prepareCandidateForDDorder(@NonNull final AbstractTransactionEvent event)
	{
		final Candidate candidate;
		final TransactionDetail transactionDetailOfEvent = createTransactionDetail(event);

		final DistributionDetail distributionDetail = DistributionDetail.builder()
				.ddOrderLineId(event.getDdOrderLineId())
				.ddOrderId(event.getDdOrderId())
				.actualQty(event.getQuantity())
				.build();

		final CandidatesQuery query = CandidatesQuery.builder()
				.distributionDetail(distributionDetail) // only search via distribution detail, ..the product and warehouse will also match, but e.g. the date probably won't!
				.build();

		final Candidate existingCandidate = candidateRepository.retrieveLatestMatchOrNull(query);

		final boolean unrelatedNewTransaction = existingCandidate == null && event instanceof TransactionCreatedEvent;
		if (unrelatedNewTransaction)
		{
			candidate = createBuilderForNewUnrelatedCandidate((TransactionCreatedEvent)event)
					.distributionDetail(distributionDetail)
					.transactionDetail(transactionDetailOfEvent)
					.build();
		}
		else if (existingCandidate != null)
		{
			candidate = newCandidateWithAddedTransactionDetail(
					existingCandidate,
					transactionDetailOfEvent);
		}
		else
		{
			throw createExceptionForUnexpectedEvent(event);
		}
		return candidate;
	}

	private AdempiereException createExceptionForUnexpectedEvent(final AbstractTransactionEvent event)
	{
		return new AdempiereException("AbstractTransactionEvent with unexpected type and not-yet-existing candidate")
				.appendParametersToMessage()
				.setParameter("abstractTransactionEvent", event);
	}

	private TransactionDetail createTransactionDetail(@NonNull final AbstractTransactionEvent event)
	{
		final TransactionDetail transactionDetailOfEvent = TransactionDetail
				.forCandidateOrQuery(
						event.getQuantityDelta(), // quantity won't be used in the query, but in the following insert or update
						event.getTransactionId());
		return transactionDetailOfEvent;
	}

	private Candidate prepareUnrelatedCandidate(@NonNull final AbstractTransactionEvent event)
	{
		final Candidate candidate;
		final TransactionDetail transactionDetailOfEvent = createTransactionDetail(event);

		final CandidatesQuery query = CandidatesQuery.builder()
				.materialDescriptorQuery(MaterialDescriptorQuery.forDescriptor(event.getMaterialDescriptor()))
				.transactionDetail(TransactionDetail.forQuery(event.getTransactionId()))
				.build();
		final Candidate existingCandidate = candidateRepository.retrieveLatestMatchOrNull(query);

		final boolean unrelatedNewTransaction = existingCandidate == null && event instanceof TransactionCreatedEvent;
		if (unrelatedNewTransaction)
		{
			candidate = createBuilderForNewUnrelatedCandidate((TransactionCreatedEvent)event)
					.transactionDetail(transactionDetailOfEvent)
					.build();
		}
		else if (existingCandidate != null)
		{
			candidate = newCandidateWithAddedTransactionDetailAndQuantity(
					existingCandidate,
					transactionDetailOfEvent);
		}
		else
		{
			throw createExceptionForUnexpectedEvent(event);
		}
		return candidate;
	}

	private Candidate newCandidateWithAddedTransactionDetailAndQuantity(
			@NonNull final Candidate candidate,
			@NonNull final TransactionDetail transactionDetail)
	{
		final BigDecimal newQuantity = candidate
				.getQuantity()
				.add(transactionDetail.getQuantity());

		Candidate newCandidate = candidate.withQuantity(newQuantity);
		newCandidate = newCandidateWithAddedTransactionDetail(newCandidate, transactionDetail);
		return newCandidate;
	}

	private Candidate newCandidateWithAddedTransactionDetail(
			@NonNull final Candidate candidate,
			@NonNull final TransactionDetail transactionDetail)
	{
		final Builder<TransactionDetail> newTransactionDetailsList = //
				ImmutableList.<TransactionDetail> builder()
						.addAll(candidate.getTransactionDetails())
						.add(transactionDetail);

		return candidate.withTransactionDetails(newTransactionDetailsList.build());
	}

	/**
	 * @param transactionCreatedEvent note that creating a new candidate doesn't make sense for a {@link TransactionDeletedEvent}
	 */
	@VisibleForTesting
	static CandidateBuilder createBuilderForNewUnrelatedCandidate(@NonNull final TransactionCreatedEvent transactionCreatedEvent)
	{
		final BigDecimal eventQuantity = transactionCreatedEvent.getQuantity();

		final CandidateBuilder builder = Candidate
				.builderForEventDescr(transactionCreatedEvent.getEventDescriptor());
		if (eventQuantity.signum() <= 0)
		{
			return builder.type(CandidateType.UNRELATED_DECREASE)
					.materialDescriptor(transactionCreatedEvent.getMaterialDescriptor().withQuantity(eventQuantity.negate()));
		}
		else
		{
			return builder.type(CandidateType.UNRELATED_INCREASE)
					.materialDescriptor(transactionCreatedEvent.getMaterialDescriptor());
		}
	}
}