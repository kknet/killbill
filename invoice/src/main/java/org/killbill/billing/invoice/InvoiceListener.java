/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.invoice;

import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.clock.Clock;
import org.killbill.billing.events.BlockingTransitionInternalEvent;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.events.EffectiveEntitlementInternalEvent;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.events.RepairSubscriptionInternalEvent;
import org.killbill.billing.util.config.InvoiceConfig;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class InvoiceListener {

    private static final Logger log = LoggerFactory.getLogger(InvoiceListener.class);

    private final InvoiceDispatcher dispatcher;
    private final InternalCallContextFactory internalCallContextFactory;
    private final AccountInternalApi accountApi;
    private final InvoiceConfig invoiceConfig;
    private final Clock clock;

    @Inject
    public InvoiceListener(final AccountInternalApi accountApi, final Clock clock, final InternalCallContextFactory internalCallContextFactory,
                           final InvoiceConfig invoiceConfig, final InvoiceDispatcher dispatcher) {
        this.accountApi = accountApi;
        this.dispatcher = dispatcher;
        this.invoiceConfig = invoiceConfig;
        this.internalCallContextFactory = internalCallContextFactory;
        this.clock = clock;
    }

    @Subscribe
    public void handleRepairSubscriptionEvent(final RepairSubscriptionInternalEvent event) {

        try {
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "RepairBundle", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
            dispatcher.processAccount(event.getAccountId(), event.getEffectiveDate(), null, context);
        } catch (InvoiceApiException e) {
            log.error(e.getMessage());
        }
    }

    @Subscribe
    public void handleSubscriptionTransition(final EffectiveSubscriptionInternalEvent event) {

        try {
            //  Skip future uncancel event
            //  Skip events which are marked as not being the last one
            if (event.getTransitionType() == SubscriptionBaseTransitionType.UNCANCEL ||
                event.getTransitionType() == SubscriptionBaseTransitionType.MIGRATE_ENTITLEMENT
                || event.getRemainingEventsForUserOperation() > 0) {
                return;
            }
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "SubscriptionBaseTransition", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
            dispatcher.processSubscription(event, context);
        } catch (InvoiceApiException e) {
            log.error(e.getMessage());
        }
    }

    @Subscribe
    public void handleEntitlementTransition(final EffectiveEntitlementInternalEvent event) {

        try {
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "SubscriptionBaseTransition", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
            dispatcher.processAccount(event.getAccountId(), event.getEffectiveTransitionTime(), null, context);
        } catch (InvoiceApiException e) {
            log.error(e.getMessage());
        }
    }

    @Subscribe
    public void handleBlockingStateTransition(final BlockingTransitionInternalEvent event) {

        // We are only interested in blockBilling or unblockBilling transitions.
        if (!event.isTransitionedToUnblockedBilling() && !event.isTransitionedToBlockedBilling()) {
            return;
        }

        try {
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "SubscriptionBaseTransition", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
            final UUID accountId = accountApi.getByRecordId(event.getSearchKey1(), context);
            dispatcher.processAccount(accountId, clock.getUTCNow(), null, context);
        } catch (InvoiceApiException e) {
            log.error(e.getMessage());
        } catch (AccountApiException e) {
            log.error(e.getMessage());
        }
    }

    public void handleNextBillingDateEvent(final UUID subscriptionId, final DateTime eventDateTime, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        try {
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, "Next Billing Date", CallOrigin.INTERNAL, UserType.SYSTEM, userToken);
            dispatcher.processSubscription(subscriptionId, eventDateTime, context);
        } catch (InvoiceApiException e) {
            log.error(e.getMessage());
        }
    }
}
