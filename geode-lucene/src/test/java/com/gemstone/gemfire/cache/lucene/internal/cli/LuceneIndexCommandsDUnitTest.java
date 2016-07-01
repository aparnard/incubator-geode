/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.gemstone.gemfire.cache.lucene.internal.cli;

import static com.gemstone.gemfire.test.dunit.LogWriterUtils.getLogWriter;
import static org.junit.Assert.*;

import com.gemstone.gemfire.management.cli.Result;
import com.gemstone.gemfire.management.internal.cli.commands.CliCommandTestBase;
import com.gemstone.gemfire.management.internal.cli.i18n.CliStrings;

import org.junit.Test;

public class LuceneIndexCommandsDUnitTest extends CliCommandTestBase {

  @Test
  public void testListIndex() throws Exception {
    final Result result = executeCommand(LuceneCliStrings.LIST_INDEX);

    assertNotNull(result);
    assertEquals(Result.Status.OK, result.getStatus());
  }

}