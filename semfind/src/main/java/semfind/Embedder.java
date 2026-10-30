package semfind;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;

/** Turns text into a unit-length vector using a local ONNX model (no internet needed). */
public final class Embedder {
    public static final String MODEL_NAME = "all-MiniLM-L6-v2";

private final AllMiniLmL6V2EmbeddingModel model;

public Embedder() {
    model = new AllMiniLmL6V2EmbeddingModel();
}

    public float[] embed(String text) {
        float[] v = model.embed(text).content().vector().clone();
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < v.length; i++) v[i] /= (float) norm;
        return v; // unit length, so dot product == cosine similarity
    }

    }

