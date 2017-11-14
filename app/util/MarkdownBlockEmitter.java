/********************************************************************************
 * Copyright (c) {date} Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the 
 * terms of the Apache License, Version 2.0 which is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0 
 ********************************************************************************/
package util;

import java.util.List;

import com.github.rjeschke.txtmark.BlockEmitter;

class MarkdownBlockEmitter implements BlockEmitter {

    static final MarkdownBlockEmitter INSTANCE = new MarkdownBlockEmitter();

    @Override
    public void emitBlock(StringBuilder out, List<String> lines, String meta) {
        if (!lines.isEmpty()) {
            if (meta == null || meta.length() == 0) {
                // default to ceylon code
                meta = "ceylon";
            }
            out.append("<pre data-language='").append(meta).append("'>");
            for (String line : lines) {
                out.append(line).append('\n');
            }
            out.append("</pre>\n");
        }
    }

}