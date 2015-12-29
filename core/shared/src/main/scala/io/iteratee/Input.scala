package io.iteratee

/**
 * Represents four functions that can be used to reduce an [[Input]] to a value.
 *
 * Combining two "functions" into a single class allows us to save allocations. `onEmpty` and `onEl`
 * may be overriden to avoid unnecessary allocations, but should be consistent with `onChunk`.
 */
abstract class InputFolder[@specialized E, A] extends Serializable {
  def onChunk(es: Vector[E]): A
  def onEnd: A
  def onEmpty: A
  def onEl(e: E): A
}

/**
 * Input to an [[Iteratee]].
 */
sealed abstract class Input[@specialized E] extends Serializable { self =>
  /**
   * Reduce this [[Input]] to a value using the given four functions.
   */
  def foldWith[A](folder: InputFolder[E, A]): A

  def isEmpty: Boolean
  def isEnd: Boolean

  def map[X](f: E => X): Input[X]
  def flatMap[X](f: E => Input[X]): Input[X]
  def filter(f: E => Boolean): Input[E]
  def foreach(f: E => Unit): Unit
  def forall(p: E => Boolean): Boolean
  def exists(p: E => Boolean): Boolean

  /**
   * Normalize the [[Input]] so that representations do not overlap.
   *
   * If this [[Input]] is a chunk with no values, an empty input will be returned, and if it's a
   * chunk with a single value, and element input will be returned.
   */
  private[iteratee] def normalize: Input[E]

  /**
   * Convert this [[Input]] value into a list of elements.
   */
  private[iteratee] def toVector: Vector[E]

  private[iteratee] final def shorter(that: Input[E]): Input[E] =
    if (isEnd || that.isEnd) Input.end
      else if (isEmpty || that.isEmpty) Input.empty
      else if (toVector.lengthCompare(that.toVector.size) < 0) this else that
}

object Input extends InputInstances {
  /**
   * An empty input value.
   */
  final def empty[E]: Input[E] = emptyValue.asInstanceOf[Input[E]]

  /**
   * An input value representing the end of a stream.
   */
  final def end[E]: Input[E] = endValue.asInstanceOf[Input[E]]

  /**
   * An input value containing a single element.
   */
  final def el[E](e: E): Input[E] = new Input[E] { self =>
    final def foldWith[A](folder: InputFolder[E, A]): A = folder.onEl(e)
    final def isEmpty: Boolean = false
    final def isEnd: Boolean = false
    final def map[X](f: E => X): Input[X] = Input.el(f(e))
    final def flatMap[X](f: E => Input[X]): Input[X] = f(e)
    final def filter(f: E => Boolean): Input[E] = if (f(e)) self else empty
    final def foreach(f: E => Unit): Unit = f(e)
    final def forall(p: E => Boolean): Boolean = p(e)
    final def exists(p: E => Boolean): Boolean = p(e)
    private[iteratee] final def normalize: Input[E] = self
    private[iteratee] final def toVector: Vector[E] = Vector(e)

  }

  /**
   * An input value containing zero or more elements.
   */
  final def chunk[E](es: Vector[E]): Input[E] = new Input[E] { self =>
    final def foldWith[A](folder: InputFolder[E, A]): A = folder.onChunk(es)
    final def isEmpty: Boolean = es.isEmpty
    final def isEnd: Boolean = false
    final def map[X](f: E => X): Input[X] = chunk(es.map(f(_)))
    final def flatMap[X](f: E => Input[X]): Input[X] = es.foldLeft(empty[X]) {
      case (acc, _) if acc.isEnd => end
      case (acc, e) =>
        val ei = f(e)
        if (ei.isEnd) end else chunk(acc.toVector ++ ei.toVector)
    }
    final def filter(f: E => Boolean): Input[E] = Input.chunk(es.filter(f))
    final def foreach(f: E => Unit): Unit = es.foreach(f(_))
    final def forall(p: E => Boolean): Boolean = es.forall(p(_))
    final def exists(p: E => Boolean): Boolean = es.exists(p(_))

    private[iteratee] final def normalize: Input[E] = {
      val c = es.lengthCompare(1)
      if (c < 0) empty else if (c == 0) el(es.head) else self
    }

    private[iteratee] final def toVector: Vector[E] = es
  }

  private[this] final val emptyValue: Input[Nothing] = new Input[Nothing] {
    def foldWith[A](folder: InputFolder[Nothing, A]): A = folder.onEmpty
    final val isEmpty: Boolean = true
    final val isEnd: Boolean = false
    final def map[X](f: Nothing => X): Input[X] = this.asInstanceOf[Input[X]]
    final def flatMap[X](f: Nothing => Input[X]): Input[X] = this.asInstanceOf[Input[X]]
    final def filter(f: Nothing => Boolean): Input[Nothing] = this
    final def foreach(f: Nothing => Unit): Unit = ()
    final def forall(p: Nothing => Boolean): Boolean = true
    final def exists(p: Nothing => Boolean): Boolean = false
    private[iteratee] final val normalize: Input[Nothing] = this
    private[iteratee] final val toVector: Vector[Nothing] = Vector.empty
  }

  private[this] final val endValue: Input[Nothing] = new Input[Nothing] {
    final def foldWith[A](folder: InputFolder[Nothing, A]): A = folder.onEnd
    final val isEmpty: Boolean = false
    final val isEnd: Boolean = true
    final def map[X](f: Nothing => X): Input[X] = this.asInstanceOf[Input[X]]
    final def flatMap[X](f: Nothing => Input[X]): Input[X] = this.asInstanceOf[Input[X]]
    final def filter(f: Nothing => Boolean): Input[Nothing] = this
    final def foreach(f: Nothing => Unit): Unit = ()
    final def forall(p: Nothing => Boolean): Boolean = true
    final def exists(p: Nothing => Boolean): Boolean = false
    private[iteratee] final val normalize: Input[Nothing] = this
    private[iteratee] final val toVector: Vector[Nothing] = Vector.empty
  }
}
