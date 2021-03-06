/**
 * generated by Xtext
 */
package org.eclipse.xtext.purexbase.validation;

import org.eclipse.xtext.purexbase.validation.AbstractPureXbaseValidator;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.xbase.XExpression;

/**
 * This class contains custom validation rules.
 * 
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
@SuppressWarnings("all")
public class PureXbaseValidator extends AbstractPureXbaseValidator {
  @Check
  @Override
  public void checkInnerExpressions(final XExpression expr) {
  }
}
