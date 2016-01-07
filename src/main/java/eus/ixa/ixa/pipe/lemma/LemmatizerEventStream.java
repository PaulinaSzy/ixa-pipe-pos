/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eus.ixa.ixa.pipe.lemma;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.AbstractEventStream;
import opennlp.tools.util.ObjectStream;

/**
 * Class for creating an event stream out of data files for training a probabilistic lemmatizer.
 */
public class LemmatizerEventStream extends AbstractEventStream<LemmaSample> {

  private LemmatizerContextGenerator cg;

  /**
   * Creates a new event stream based on the specified data stream using the specified context generator.
   * @param d The data stream for this event stream.
   * @param cg The context generator which should be used in the creation of events for this event stream.
   */
  public LemmatizerEventStream(ObjectStream<LemmaSample> d, LemmatizerContextGenerator cg) {
    super(d);
    this.cg = cg;
  }

  @Override
  protected Iterator<Event> createEvents(LemmaSample sample) {

    if (sample != null) {
      List<Event> events = new ArrayList<Event>();
      String[] toksArray = sample.getTokens();
      String[] tagsArray = sample.getTags();
      String[] predsArray = sample.getPreds();
      for (int ei = 0, el = sample.getTokens().length; ei < el; ei++) {
        events.add(new Event(predsArray[ei], cg.getContext(ei,toksArray,tagsArray,predsArray)));
      }
      return events.iterator();
    }
    else {
      return Collections.emptyListIterator();
    }
  }
}
