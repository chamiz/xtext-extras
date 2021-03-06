/**
 * Copyright (c) 2014 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.xtext.xbase.formatting2;

import org.eclipse.xtend.lib.annotations.Accessors;
import org.eclipse.xtend.lib.annotations.FinalFieldsConstructor;
import org.eclipse.xtext.formatting2.ITextReplacer;
import org.eclipse.xtext.formatting2.ITextReplacerContext;
import org.eclipse.xtext.formatting2.regionaccess.ISemanticRegion;
import org.eclipse.xtext.xbase.lib.Pure;

/**
 * @author Moritz Eysholdt - Initial implementation and API
 */
@FinalFieldsConstructor
@SuppressWarnings("all")
public class ArrayBracketsFormattingReplacer implements ITextReplacer {
  @Accessors
  private final ISemanticRegion region;
  
  @Override
  public ITextReplacerContext createReplacements(final ITextReplacerContext it) {
    final String t = this.region.getText();
    final int offset = this.region.getOffset();
    for (int i = 0; (i < t.length()); i++) {
      boolean _isWhitespace = Character.isWhitespace(t.charAt(i));
      if (_isWhitespace) {
        it.addReplacement(this.region.getTextRegionAccess().getRewriter().createReplacement((offset + i), 1, ""));
      }
    }
    return it;
  }
  
  @Pure
  public ISemanticRegion getRegion() {
    return this.region;
  }
  
  public ArrayBracketsFormattingReplacer(final ISemanticRegion region) {
    super();
    this.region = region;
  }
}
