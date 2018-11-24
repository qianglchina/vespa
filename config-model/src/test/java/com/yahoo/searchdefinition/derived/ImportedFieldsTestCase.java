// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

/**
 * @author geirst
 */
public class ImportedFieldsTestCase extends AbstractExportingTestCase {

    @Test
    public void configs_for_imported_fields_are_derived() throws IOException, ParseException {
        assertCorrectDeriving("importedfields", "child");
    }

    @Test
    public void configs_for_imported_struct_fields_are_derived() throws IOException, ParseException {
        assertCorrectDeriving("imported_struct_fields", "child");
    }
}
