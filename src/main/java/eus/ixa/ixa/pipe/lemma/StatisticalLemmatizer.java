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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.ml.BeamSearch;
import opennlp.tools.ml.EventModelSequenceTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.SequenceTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.TrainerFactory.TrainerType;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.TrainingParameters;

/**
 * A probabilistic lemmatizer.  Tries to predict the induced permutation class
 * for each word depending on its surrounding context.
 */
public class StatisticalLemmatizer {
  
  public static final int DEFAULT_BEAM_SIZE = 3;
  protected int beamSize;
  private Sequence bestSequence;
  
  private SequenceClassificationModel<String> model;
  
  private LemmatizerContextGenerator contextGenerator;
  private SequenceValidator<String> sequenceValidator;
  
  /**
   * Initializes the current instance with the provided model
   * and the default beam size of 3.
   *
   * @param model the model
   */
  public StatisticalLemmatizer(LemmatizerModel model) {
    
    LemmatizerFactory factory = model.getFactory();
    int defaultBeamSize = StatisticalLemmatizer.DEFAULT_BEAM_SIZE;
    String beamSizeString = model.getManifestProperty(BeamSearch.BEAM_SIZE_PARAMETER);
    if (beamSizeString != null) {
      defaultBeamSize = Integer.parseInt(beamSizeString);
    }
    
    contextGenerator = factory.getContextGenerator();
    beamSize = defaultBeamSize;

    sequenceValidator = factory.getSequenceValidator();

    if (model.getLemmatizerSequenceModel() != null) {
      this.model = model.getLemmatizerSequenceModel();
    }
    else {
      this.model = new opennlp.tools.ml.BeamSearch<String>(beamSize,
          (MaxentModel) model.getLemmatizerSequenceModel(), 0);
    }
  }
  
  public String[] lemmatize(String[] toks, String[] tags) {
    bestSequence = model.bestSequence(toks, new Object[] {tags}, contextGenerator, sequenceValidator);
    List<String> c = bestSequence.getOutcomes();
    return c.toArray(new String[c.size()]);
  }
  
  public Sequence[] topKSequences(String[] sentence, String[] tags) {
    return model.bestSequences(DEFAULT_BEAM_SIZE, sentence,
        new Object[] { tags }, contextGenerator, sequenceValidator);
  }

  public Sequence[] topKSequences(String[] sentence, String[] tags, double minSequenceScore) {
    return model.bestSequences(DEFAULT_BEAM_SIZE, sentence, new Object[] { tags }, minSequenceScore,
        contextGenerator, sequenceValidator);
  }

  /**
   * Populates the specified array with the probabilities of the last decoded sequence.  The
   * sequence was determined based on the previous call to <code>lemmatize</code>.  The
   * specified array should be at least as large as the number of tokens in the previous call to <code>lemmatize</code>.
   *
   * @param probs An array used to hold the probabilities of the last decoded sequence.
   */
  public void probs(double[] probs) {
    bestSequence.getProbs(probs);
  }

    /**
     * Returns an array with the probabilities of the last decoded sequence.  The
     * sequence was determined based on the previous call to <code>chunk</code>.
     * @return An array with the same number of probabilities as tokens were sent to <code>chunk</code>
     * when it was last called.
     */
  public double[] probs() {
    return bestSequence.getProbs();
  }
  
  public static LemmatizerModel train(String languageCode,
      ObjectStream<LemmaSample> samples, TrainingParameters trainParams,
      LemmatizerFactory posFactory) throws IOException {

    String beamSizeString = trainParams.getSettings().get(BeamSearch.BEAM_SIZE_PARAMETER);

    int beamSize = StatisticalLemmatizer.DEFAULT_BEAM_SIZE;
    if (beamSizeString != null) {
      beamSize = Integer.parseInt(beamSizeString);
    }

    LemmatizerContextGenerator contextGenerator = posFactory.getContextGenerator();

    Map<String, String> manifestInfoEntries = new HashMap<String, String>();

    TrainerType trainerType = TrainerFactory.getTrainerType(trainParams.getSettings());

    MaxentModel lemmatizerModel = null;
    SequenceClassificationModel<String> seqLemmatizerModel = null;
    if (TrainerType.EVENT_MODEL_TRAINER.equals(trainerType)) {
      ObjectStream<Event> es = new LemmaSampleEventStream(samples, contextGenerator);

      EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams.getSettings(),
          manifestInfoEntries);
      lemmatizerModel = trainer.train(es);
    }
    else if (TrainerType.EVENT_MODEL_SEQUENCE_TRAINER.equals(trainerType)) {
      LemmaSampleSequenceStream ss = new LemmaSampleSequenceStream(samples, contextGenerator);
      EventModelSequenceTrainer trainer = TrainerFactory.getEventModelSequenceTrainer(trainParams.getSettings(),
          manifestInfoEntries);
      lemmatizerModel = trainer.train(ss);
    }
    else if (TrainerType.SEQUENCE_TRAINER.equals(trainerType)) {
      SequenceTrainer trainer = TrainerFactory.getSequenceModelTrainer(
          trainParams.getSettings(), manifestInfoEntries);

      // TODO: This will probably cause issue, since the feature generator uses the outcomes array

      LemmaSampleSequenceStream ss = new LemmaSampleSequenceStream(samples, contextGenerator);
      seqLemmatizerModel = trainer.train(ss);
    }
    else {
      throw new IllegalArgumentException("Trainer type is not supported: " + trainerType);
    }

    if (lemmatizerModel != null) {
      return new LemmatizerModel(languageCode, lemmatizerModel, beamSize, manifestInfoEntries, posFactory);
    }
    else {
      return new LemmatizerModel(languageCode, seqLemmatizerModel, manifestInfoEntries, posFactory);
    }
  }
  
}
