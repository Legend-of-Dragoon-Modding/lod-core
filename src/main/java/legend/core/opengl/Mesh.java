package legend.core.opengl;

import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glDrawElements;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL20C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;

public class Mesh {
  private final int vao;
  private final int count;
  private final int mode;
  private final boolean useIndices;

  public Mesh(final int mode, final float[] vertices, final int[] indices) {
    this.count = indices.length;
    this.mode = mode;
    this.useIndices = true;

    this.vao = glGenVertexArrays();
    glBindVertexArray(this.vao);

    final int vbo = glGenBuffers();
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

    final int ebo = glGenBuffers();
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

    glBindVertexArray(0);
  }

  public Mesh(final int mode, final float[] vertices, final int count) {
    this.count = count;
    this.mode = mode;
    this.useIndices = false;

    this.vao = glGenVertexArrays();
    glBindVertexArray(this.vao);

    final int vbo = glGenBuffers();
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

    glBindVertexArray(0);
  }

  public void attribute(final int index, final long offset, final int size, final int stride) {
    glBindVertexArray(this.vao);
    glVertexAttribPointer(index, size, GL_FLOAT, false, stride * Float.BYTES, offset * Float.BYTES);
    glEnableVertexAttribArray(index);
    glBindVertexArray(0);
  }

  public void draw() {
    glBindVertexArray(this.vao);

    if(this.useIndices) {
      glDrawElements(this.mode, this.count, GL_UNSIGNED_INT, 0L);
    } else {
      glDrawArrays(this.mode, 0, this.count);
    }
  }
}
