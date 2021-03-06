/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.el;

import static java.lang.Boolean.valueOf;
import static java.lang.System.getProperty;
import static java.util.regex.Pattern.compile;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.join;
import static org.mule.runtime.core.api.config.MuleProperties.MULE_MEL_AS_DEFAULT;
import static org.mule.runtime.core.el.DefaultExpressionManager.DW_PREFIX;
import static org.mule.runtime.core.el.DefaultExpressionManager.MEL_PREFIX;

import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.el.BindingContext;
import org.mule.runtime.api.el.ValidationResult;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.InternalEvent;
import org.mule.runtime.core.api.el.ExtendedExpressionLanguageAdaptor;
import org.mule.runtime.core.api.expression.ExpressionRuntimeException;
import org.mule.runtime.core.el.mvel.MVELExpressionLanguage;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of an {@link ExtendedExpressionLanguageAdaptor} which adapts MVEL and DW together, deciding via a prefix whether
 * one or the other should be call. It will allow MVEL and DW to be used together in compatibility mode.
 *
 * @since 4.0
 */
public class ExpressionLanguageAdaptorHandler implements ExtendedExpressionLanguageAdaptor {

  private static final String EXPR_PREFIX_LANGS_TOKEN = "LANGS";
  private static final String EXPR_PREFIX_PATTERN_TEMPLATE =
      "^\\s*(?:(?:#\\[)?\\s*(" + EXPR_PREFIX_LANGS_TOKEN + "):|\\%(" + EXPR_PREFIX_LANGS_TOKEN + ") \\d).*";

  private final Pattern exprPrefixPattern;
  private Map<String, ExtendedExpressionLanguageAdaptor> expressionLanguages;

  private boolean melDefault = false;

  public ExpressionLanguageAdaptorHandler(DataWeaveExpressionLanguageAdaptor defaultExtendedExpressionLanguage,
                                          MVELExpressionLanguage mvelExpressionLanguage) {
    expressionLanguages = new HashMap<>();
    expressionLanguages.put(DW_PREFIX, defaultExtendedExpressionLanguage);
    expressionLanguages.put(MEL_PREFIX, mvelExpressionLanguage);

    exprPrefixPattern = compile(EXPR_PREFIX_PATTERN_TEMPLATE.replaceAll("LANGS", join(expressionLanguages.keySet(), '|')));

    melDefault = valueOf(getProperty(MULE_MEL_AS_DEFAULT, "false"));
  }

  public boolean isMelDefault() {
    return melDefault;
  }

  @Override
  public void addGlobalBindings(BindingContext bindingContext) {
    expressionLanguages.get(DW_PREFIX).addGlobalBindings(bindingContext);
  }

  @Override
  public TypedValue evaluate(String expression, InternalEvent event, BindingContext context)
      throws ExpressionRuntimeException {
    return selectExpressionLanguage(expression).evaluate(expression, event, context);
  }

  @Override
  public TypedValue evaluate(String expression, DataType expectedOutputType, InternalEvent event, BindingContext context)
      throws ExpressionRuntimeException {
    return selectExpressionLanguage(expression).evaluate(expression, expectedOutputType, event, context);
  }

  @Override
  public TypedValue evaluate(String expression, DataType expectedOutputType, InternalEvent event,
                             ComponentLocation componentLocation,
                             BindingContext context, boolean failOnNull)
      throws ExpressionRuntimeException {
    return selectExpressionLanguage(expression).evaluate(expression, expectedOutputType, event, componentLocation, context,
                                                         failOnNull);
  }

  @Override
  public TypedValue evaluate(String expression, InternalEvent event, ComponentLocation componentLocation,
                             BindingContext bindingContext)
      throws ExpressionRuntimeException {
    return selectExpressionLanguage(expression).evaluate(expression, event, componentLocation, bindingContext);
  }

  @Override
  public ValidationResult validate(String expression) {
    return selectExpressionLanguage(expression).validate(expression);
  }

  @Override
  public TypedValue evaluate(String expression, InternalEvent event, InternalEvent.Builder eventBuilder,
                             ComponentLocation componentLocation,
                             BindingContext bindingContext)
      throws ExpressionRuntimeException {
    return selectExpressionLanguage(expression).evaluate(expression, event, eventBuilder, componentLocation, bindingContext);
  }

  @Override
  public void enrich(String expression, InternalEvent event, InternalEvent.Builder eventBuilder,
                     ComponentLocation componentLocation,
                     Object object) {
    selectExpressionLanguage(expression).enrich(expression, event, eventBuilder, componentLocation, object);
  }

  @Override
  public void enrich(String expression, InternalEvent event, InternalEvent.Builder eventBuilder,
                     ComponentLocation componentLocation,
                     TypedValue value) {
    selectExpressionLanguage(expression).enrich(expression, event, eventBuilder, componentLocation, value);
  }

  @Override
  public Iterator<TypedValue<?>> split(String expression, InternalEvent event, ComponentLocation componentLocation,
                                       BindingContext bindingContext)
      throws ExpressionRuntimeException {
    return selectExpressionLanguage(expression).split(expression, event, componentLocation, bindingContext);
  }

  @Override
  public Iterator<TypedValue<?>> split(String expression, InternalEvent event, BindingContext bindingContext)
      throws ExpressionRuntimeException {
    return selectExpressionLanguage(expression).split(expression, event, bindingContext);
  }

  private ExtendedExpressionLanguageAdaptor selectExpressionLanguage(String expression) {
    final String languagePrefix = getLanguagePrefix(expression);
    if (isEmpty(languagePrefix)) {
      if (melDefault) {
        return expressionLanguages.get(MEL_PREFIX);
      } else {
        return expressionLanguages.get(DW_PREFIX);
      }
    } else {
      return expressionLanguages.get(languagePrefix);
    }
  }

  private String getLanguagePrefix(String expression) {
    final Matcher matcher = exprPrefixPattern.matcher(expression);
    if (matcher.find()) {
      int i = 1;
      String currentGroup = null;
      while (currentGroup == null) {
        currentGroup = matcher.group(i++);
      }
      return currentGroup;
    } else {
      return null;
    }
  }
}
