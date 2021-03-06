/*
 * Copyright 2010-2014 Ning, Inc.
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

package org.killbill.billing.invoice.tree;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.generator.InvoiceDateUtils;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.invoice.model.RepairAdjInvoiceItem;
import org.killbill.billing.util.currency.KillBillMoney;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

/**
 * An generic invoice item that contains all pertinent fields regarding of its InvoiceItemType.
 * <p/>
 * It contains an action that determines what to do when building the tree (whether in normal or merge mode). It also
 * keeps track of current adjusted and repair amount so subsequent repair can be limited to what is left.
 */
public class Item {

    private final UUID id;
    private final UUID accountId;
    private final UUID bundleId;
    private final UUID subscriptionId;
    private final UUID targetInvoiceId;
    private final UUID invoiceId;
    private final String planName;
    private final String phaseName;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal amount;
    private final BigDecimal rate;
    private final Currency currency;
    private final DateTime createdDate;
    private final UUID linkedId;

    private BigDecimal currentRepairedAmount;
    private BigDecimal adjustedAmount;

    private final ItemAction action;

    public enum ItemAction {
        ADD,
        CANCEL
    }

    public Item(final Item item, final ItemAction action) {
        this.id = item.id;
        this.accountId = item.accountId;
        this.bundleId = item.bundleId;
        this.subscriptionId = item.subscriptionId;
        this.targetInvoiceId = item.targetInvoiceId;
        this.invoiceId = item.invoiceId;
        this.planName = item.planName;
        this.phaseName = item.phaseName;
        this.startDate = item.startDate;
        this.endDate = item.endDate;
        this.amount = item.amount;
        this.rate = item.rate;
        this.currency = item.currency;
        // In merge mode, the reverse item needs to correctly point to itself (repair of original item)
        this.linkedId = action == ItemAction.ADD ? item.linkedId : this.id;
        this.createdDate = item.createdDate;
        this.currentRepairedAmount = item.currentRepairedAmount;
        this.adjustedAmount = item.adjustedAmount;

        this.action = action;
    }

    public Item(final InvoiceItem item, final UUID targetInvoiceId, final ItemAction action) {
        this.id = item.getId();
        this.accountId = item.getAccountId();
        this.bundleId = item.getBundleId();
        this.subscriptionId = item.getSubscriptionId();
        this.targetInvoiceId = targetInvoiceId;
        this.invoiceId = item.getInvoiceId();
        this.planName = item.getPlanName();
        this.phaseName = item.getPhaseName();
        this.startDate = item.getStartDate();
        this.endDate = item.getEndDate();
        this.amount = item.getAmount().abs();
        this.rate = item.getRate();
        this.currency = item.getCurrency();
        this.linkedId = item.getLinkedItemId();
        this.createdDate = item.getCreatedDate();
        this.action = action;

        this.currentRepairedAmount = BigDecimal.ZERO;
        this.adjustedAmount = BigDecimal.ZERO;
    }

    public InvoiceItem toInvoiceItem() {
        return toProratedInvoiceItem(startDate, endDate);
    }

    public InvoiceItem toProratedInvoiceItem(final LocalDate newStartDate, final LocalDate newEndDate) {

        int nbTotalDays = Days.daysBetween(startDate, endDate).getDays();
        final boolean prorated = !(newStartDate.compareTo(startDate) == 0 && newEndDate.compareTo(endDate) == 0);

        // Pro-ration is built by using the startDate, endDate and amount of this item instead of using the rate and a potential full period.
        final BigDecimal positiveAmount = prorated ? InvoiceDateUtils.calculateProrationBetweenDates(newStartDate, newEndDate, nbTotalDays)
                                                                     .multiply(amount) : amount;

        if (action == ItemAction.ADD) {
            return new RecurringInvoiceItem(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, newStartDate, newEndDate, KillBillMoney.of(positiveAmount, currency), rate, currency);
        } else {
            // We first compute the maximum amount after adjustment and that sets the amount limit of how much can be repaired.
            final BigDecimal maxAvailableAmountAfterAdj = amount.subtract(adjustedAmount);
            final BigDecimal maxAvailableAmountForRepair = maxAvailableAmountAfterAdj.subtract(currentRepairedAmount);
            final BigDecimal positiveAmountForRepair = positiveAmount.compareTo(maxAvailableAmountForRepair) <= 0 ? positiveAmount : maxAvailableAmountForRepair;
            return positiveAmountForRepair.compareTo(BigDecimal.ZERO) > 0 ? new RepairAdjInvoiceItem(targetInvoiceId, accountId, newStartDate, newEndDate, KillBillMoney.of(positiveAmountForRepair.negate(), currency), currency, linkedId) : null;
        }
    }

    public void incrementAdjustedAmount(final BigDecimal increment) {
        Preconditions.checkState(increment.compareTo(BigDecimal.ZERO) > 0);
        adjustedAmount = adjustedAmount.add(increment);
    }

    public void incrementCurrentRepairedAmount(final BigDecimal increment) {
        Preconditions.checkState(increment.compareTo(BigDecimal.ZERO) > 0);
        currentRepairedAmount = currentRepairedAmount.add(increment);
    }

    public ItemAction getAction() {
        return action;
    }

    public UUID getLinkedId() {
        return linkedId;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getId() {
        return id;
    }

    public Currency getCurrency() {
        return currency;
    }

    /**
     * Compare two items to check whether there are the same kind; that is whether or not they build for the same product/plan.
     *
     * @param other item to compare with
     * @return
     */
    public boolean isSameKind(final Item other) {

        final InvoiceItem otherItem = other.toInvoiceItem();

        return !id.equals(otherItem.getId()) &&
               // Finally, for the tricky part... In case of complete repairs, the new invoiceItem will always meet all of the
               // following conditions: same type, subscription, start date. Depending on the catalog configuration, the end
               // date check could also match (e.g. repair from annual to monthly). For that scenario, we need to default
               // to catalog checks (the rate check is a lame check for versioned catalogs).
               Objects.firstNonNull(planName, "").equals(Objects.firstNonNull(otherItem.getPlanName(), "")) &&
               Objects.firstNonNull(phaseName, "").equals(Objects.firstNonNull(otherItem.getPhaseName(), "")) &&
               Objects.firstNonNull(rate, BigDecimal.ZERO).compareTo(Objects.firstNonNull(otherItem.getRate(), BigDecimal.ZERO)) == 0;
    }
}
