/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.tenant.api;

import java.util.List;
import java.util.Locale;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;

public interface TenantInternalApi {

    public interface CacheInvalidationCallback {
        public void invalidateCache(InternalTenantContext tenantContext);
    }

    public void initializeCacheInvalidationCallback(final TenantKey key, final CacheInvalidationCallback cacheInvalidationCallback);

    public List<String> getTenantCatalogs(InternalTenantContext tenantContext);

    public String getTenantOverdueConfig(InternalTenantContext tenantContext);

    public String getInvoiceTemplate(Locale locale, InternalTenantContext tenantContext);

    public String getManualPayInvoiceTemplate(Locale locale, InternalTenantContext tenantContext);

    public String getInvoiceTranslation(Locale locale, InternalTenantContext tenantContext);

    public String getCatalogTranslation(Locale locale, InternalTenantContext tenantContext);
}
