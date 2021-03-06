/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.runtimefields.mapper;

import org.apache.lucene.search.MultiTermQuery.RewriteMethod;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.lucene.BytesRefs;
import org.elasticsearch.common.time.DateMathParser;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.index.mapper.KeywordFieldMapper;
import org.elasticsearch.index.mapper.RuntimeField;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.runtimefields.fielddata.StringScriptFieldData;
import org.elasticsearch.runtimefields.query.StringScriptFieldExistsQuery;
import org.elasticsearch.runtimefields.query.StringScriptFieldFuzzyQuery;
import org.elasticsearch.runtimefields.query.StringScriptFieldPrefixQuery;
import org.elasticsearch.runtimefields.query.StringScriptFieldRangeQuery;
import org.elasticsearch.runtimefields.query.StringScriptFieldRegexpQuery;
import org.elasticsearch.runtimefields.query.StringScriptFieldTermQuery;
import org.elasticsearch.runtimefields.query.StringScriptFieldTermsQuery;
import org.elasticsearch.runtimefields.query.StringScriptFieldWildcardQuery;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.lookup.SearchLookup;

import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toSet;

public final class KeywordScriptFieldType extends AbstractScriptFieldType<StringFieldScript.LeafFactory> {

    public static final RuntimeField.Parser PARSER = new RuntimeField.Parser((name, parserContext) -> new Builder(name) {
        @Override
        protected RuntimeField buildFieldType() {
            if (script.get() == null) {
                return new KeywordScriptFieldType(name, StringFieldScript.PARSE_FROM_SOURCE, getScript(), meta(), this);
            }
            StringFieldScript.Factory factory = parserContext.scriptCompiler().compile(script.getValue(), StringFieldScript.CONTEXT);
            return new KeywordScriptFieldType(name, factory, getScript(), meta(), this);
        }
    });

    public KeywordScriptFieldType(String name) {
        this(name, StringFieldScript.PARSE_FROM_SOURCE, null, Collections.emptyMap(), (builder, params) -> builder);
    }

    KeywordScriptFieldType(
        String name,
        StringFieldScript.Factory scriptFactory,
        Script script,
        Map<String, String> meta,
        ToXContent toXContent
    ) {
        super(name, scriptFactory::newFactory, script, meta, toXContent);
    }

    @Override
    public String typeName() {
        return KeywordFieldMapper.CONTENT_TYPE;
    }

    @Override
    public Object valueForDisplay(Object value) {
        if (value == null) {
            return null;
        }
        // keywords are internally stored as utf8 bytes
        BytesRef binaryValue = (BytesRef) value;
        return binaryValue.utf8ToString();
    }

    @Override
    public StringScriptFieldData.Builder fielddataBuilder(String fullyQualifiedIndexName, Supplier<SearchLookup> searchLookup) {
        return new StringScriptFieldData.Builder(name(), leafFactory(searchLookup.get()));
    }

    @Override
    public Query existsQuery(SearchExecutionContext context) {
        checkAllowExpensiveQueries(context);
        return new StringScriptFieldExistsQuery(script, leafFactory(context), name());
    }

    @Override
    public Query fuzzyQuery(
        Object value,
        Fuzziness fuzziness,
        int prefixLength,
        int maxExpansions,
        boolean transpositions,
        SearchExecutionContext context
    ) {
        checkAllowExpensiveQueries(context);
        return StringScriptFieldFuzzyQuery.build(
            script,
            leafFactory(context),
            name(),
            BytesRefs.toString(Objects.requireNonNull(value)),
            fuzziness.asDistance(BytesRefs.toString(value)),
            prefixLength,
            transpositions
        );
    }

    @Override
    public Query prefixQuery(String value, RewriteMethod method, boolean caseInsensitive, SearchExecutionContext context) {
        checkAllowExpensiveQueries(context);
        return new StringScriptFieldPrefixQuery(script, leafFactory(context), name(), value, caseInsensitive);
    }

    @Override
    public Query rangeQuery(
        Object lowerTerm,
        Object upperTerm,
        boolean includeLower,
        boolean includeUpper,
        ZoneId timeZone,
        DateMathParser parser,
        SearchExecutionContext context
    ) {
        checkAllowExpensiveQueries(context);
        return new StringScriptFieldRangeQuery(
            script,
            leafFactory(context),
            name(),
            lowerTerm == null ? null : BytesRefs.toString(lowerTerm),
            upperTerm == null ? null : BytesRefs.toString(upperTerm),
            includeLower,
            includeUpper
        );
    }

    @Override
    public Query regexpQuery(
        String value,
        int syntaxFlags,
        int matchFlags,
        int maxDeterminizedStates,
        RewriteMethod method,
        SearchExecutionContext context
    ) {
        checkAllowExpensiveQueries(context);
        if (matchFlags != 0) {
            throw new IllegalArgumentException("Match flags not yet implemented [" + matchFlags + "]");
        }
        return new StringScriptFieldRegexpQuery(
            script,
            leafFactory(context),
            name(),
            value,
            syntaxFlags,
            matchFlags,
            maxDeterminizedStates
        );
    }

    @Override
    public Query termQueryCaseInsensitive(Object value, SearchExecutionContext context) {
        checkAllowExpensiveQueries(context);
        return new StringScriptFieldTermQuery(
            script,
            leafFactory(context),
            name(),
            BytesRefs.toString(Objects.requireNonNull(value)),
            true
        );
    }

    @Override
    public Query termQuery(Object value, SearchExecutionContext context) {
        checkAllowExpensiveQueries(context);
        return new StringScriptFieldTermQuery(
            script,
            leafFactory(context),
            name(),
            BytesRefs.toString(Objects.requireNonNull(value)),
            false
        );
    }

    @Override
    public Query termsQuery(Collection<?> values, SearchExecutionContext context) {
        checkAllowExpensiveQueries(context);
        Set<String> terms = values.stream().map(v -> BytesRefs.toString(Objects.requireNonNull(v))).collect(toSet());
        return new StringScriptFieldTermsQuery(script, leafFactory(context), name(), terms);
    }

    @Override
    public Query wildcardQuery(String value, RewriteMethod method, boolean caseInsensitive, SearchExecutionContext context) {
        checkAllowExpensiveQueries(context);
        return new StringScriptFieldWildcardQuery(script, leafFactory(context), name(), value, caseInsensitive);
    }
}
