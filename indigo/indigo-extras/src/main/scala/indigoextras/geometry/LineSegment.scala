package indigoextras.geometry

import indigo.shared.EqualTo
import indigo.shared.EqualTo._
import indigo.shared.datatypes.Vector2

final case class LineSegment(start: Vertex, end: Vertex) {
  val center: Vertex =
    Vertex(
      ((end.x - start.x) / 2) + start.x,
      ((end.y - start.y) / 2) + start.y
    )

  def left: Double   = Math.min(start.x, end.x)
  def right: Double  = Math.max(start.x, end.x)
  def top: Double    = Math.min(start.y, end.y)
  def bottom: Double = Math.max(start.y, end.y)

  def sdf(vertex: Vertex): Double =
    LineSegment.signedDistanceFunction(vertex, start, end)
  def distanceToBoundary(vertex: Vertex): Double =
    sdf(vertex)

  def moveTo(newPosition: Vertex): LineSegment =
    this.copy(start = newPosition, end = newPosition + (end - start))
  def moveTo(x: Double, y: Double): LineSegment =
    moveTo(Vertex(x, y))

  def moveBy(amount: Vertex): LineSegment =
    moveTo(start + amount)
  def moveBy(x: Double, y: Double): LineSegment =
    moveBy(Vertex(x, y))

  def moveStartTo(newPosition: Vertex): LineSegment =
    this.copy(start = newPosition)
  def moveStartTo(x: Double, y: Double): LineSegment =
    moveStartTo(Vertex(x, y))
  def moveStartBy(amount: Vertex): LineSegment =
    moveStartTo(start + amount)
  def moveStartBy(x: Double, y: Double): LineSegment =
    moveStartBy(Vertex(x, y))

  def moveEndTo(newPosition: Vertex): LineSegment =
    this.copy(end = newPosition)
  def moveEndTo(x: Double, y: Double): LineSegment =
    moveEndTo(Vertex(x, y))
  def moveEndBy(amount: Vertex): LineSegment =
    moveEndTo(end + amount)
  def moveEndBy(x: Double, y: Double): LineSegment =
    moveEndBy(Vertex(x, y))

  def invert: LineSegment =
    LineSegment(end, start)
  def flip: LineSegment =
    invert

  def normal: Vector2 =
    LineSegment.calculateNormal(start, end)

  def lineProperties: LineProperties =
    LineSegment.calculateLineComponents(start, end)

  def intersectWith(other: LineSegment): IntersectionResult =
    LineSegment.intersection(this, other)

  def intersectWithLine(other: LineSegment): Boolean =
    LineSegment.intersection(this, other) match {
      case IntersectionResult.NoIntersection =>
        false

      case r @ IntersectionResult.IntersectionVertex(_, _) =>
        val pt = r.toVertex
        contains(pt) && other.contains(pt)

    }

  def contains(vertex: Vertex): Boolean =
    LineSegment.lineContainsVertex(this, vertex, 0.5f)

  def isFacingVertex(vertex: Vertex): Boolean =
    LineSegment.isFacingVertex(this, vertex)

  def closestPointOnLine(to: Vertex): Option[Vertex] = {
    val a   = end.y - start.y
    val b   = start.x - end.x
    val c1  = a * start.x + b * start.y
    val c2  = -b * to.x + a * to.y
    val det = a * a - -b * b

    if (det != 0.0)
      Some(
        Vertex(
          x = (a * c1 - b * c2) / det,
          y = (a * c2 - -b * c1) / det
        ).clamp(start, end)
      )
    else
      None
  }

  def ===(other: LineSegment): Boolean =
    implicitly[EqualTo[LineSegment]].equal(this, other)

}

object LineSegment {

  implicit val lsEqualTo: EqualTo[LineSegment] = {
    val eqPt = implicitly[EqualTo[Vertex]]

    EqualTo.create { (a, b) =>
      eqPt.equal(a.start, b.start) && eqPt.equal(a.end, b.end)
    }
  }

  def apply(start: Vertex, end: Vertex): LineSegment =
    new LineSegment(start, end)

  def apply(x1: Double, y1: Double, x2: Double, y2: Double): LineSegment =
    LineSegment(Vertex(x1, y1), Vertex(x2, y2))

  def apply(start: (Double, Double), end: (Double, Double)): LineSegment =
    LineSegment(Vertex.tuple2ToVertex(start), Vertex.tuple2ToVertex(end))

  /*
  y = mx + b

  We're trying to calculate m and b where
  m is the slope i.e. number of y units per x unit
  b is the y-intersect i.e. the point on the y-axis where the line passes through it
   */
  def calculateLineComponents(start: Vertex, end: Vertex): LineProperties =
    (start, end) match {
      case (Vertex(x1, y1), Vertex(x2, y2)) if x1 === x2 && y1 === y2 =>
        LineProperties.InvalidLine

      case (Vertex(x1, _), Vertex(x2, _)) if x1 === x2 =>
        LineProperties.ParallelToAxisY

      case (Vertex(x1, y1), Vertex(x2, y2)) =>
        val m: Double = (y2 - y1) / (x2 - x1)

        LineProperties.LineComponents(m, y1 - (m * x1))
    }

  def intersection(l1: LineSegment, l2: LineSegment): IntersectionResult =
    /*
    y-intercept = mx + b (i.e. y = mx + b)
    x-intercept = -b/m   (i.e. x = -b/m where y is moved to 0)
     */
    (l1.lineProperties, l2.lineProperties) match {
      case (LineProperties.LineComponents(m1, _), LineProperties.LineComponents(m2, _)) if m1 === m2 =>
        // Same slope, so parallel
        IntersectionResult.NoIntersection

      case (LineProperties.LineComponents(m1, b1), LineProperties.LineComponents(m2, b2)) =>
        //x = -b/m
        val x: Double = (b2 - b1) / (m1 - m2)

        //y = mx + b
        val y: Double = (m1 * x) + b1

        IntersectionResult.IntersectionVertex(x, y)

      case (LineProperties.ParallelToAxisY, LineProperties.LineComponents(m, b)) =>
        IntersectionResult.IntersectionVertex(
          x = l1.start.x,
          y = (m * l1.start.x) + b
        )

      case (LineProperties.LineComponents(m, b), LineProperties.ParallelToAxisY) =>
        IntersectionResult.IntersectionVertex(
          x = l2.start.x,
          y = (m * l2.start.x) + b
        )

      case _ =>
        IntersectionResult.NoIntersection
    }

  def calculateNormal(start: Vertex, end: Vertex): Vector2 =
    normaliseVertex(Vector2(-(end.y - start.y), (end.x - start.x)))

  def normaliseVertex(vec2: Vector2): Vector2 = {
    val x: Double = vec2.x
    val y: Double = vec2.y

    Vector2(
      if (x === 0) 0 else (x / Math.abs(x)),
      if (y === 0) 0 else (y / Math.abs(y))
    )
  }

  def lineContainsVertex(lineSegment: LineSegment, point: Vertex): Boolean =
    lineContainsVertex(lineSegment, point, 0.001d)

  def lineContainsVertex(lineSegment: LineSegment, point: Vertex, tolerance: Double): Boolean =
    lineSegment.lineProperties match {
      case LineProperties.InvalidLine =>
        false

      case LineProperties.ParallelToAxisY =>
        if (point.x >= lineSegment.start.x - tolerance && point.x <= lineSegment.start.x + tolerance && point.y >= lineSegment.top && point.y <= lineSegment.bottom) true
        else false

      case LineProperties.LineComponents(m, b) =>
        if (point.x >= lineSegment.left && point.x <= lineSegment.right && point.y >= lineSegment.top && point.y <= lineSegment.bottom)
          slopeCheck(point.x, point.y, m, b, tolerance)
        else false
    }

  def lineContainsCoords(lineSegment: LineSegment, coords: (Double, Double), tolerance: Double): Boolean =
    lineContainsXY(lineSegment, coords._1, coords._2, tolerance)

  def lineContainsXY(lineSegment: LineSegment, x: Double, y: Double, tolerance: Double): Boolean =
    lineSegment.lineProperties match {
      case LineProperties.InvalidLine =>
        false

      case LineProperties.ParallelToAxisY =>
        if (x === lineSegment.start.x && y >= lineSegment.top && y <= lineSegment.bottom) true
        else false

      case LineProperties.LineComponents(m, b) =>
        if (x >= lineSegment.left && x <= lineSegment.right && y >= lineSegment.top && y <= lineSegment.bottom)
          slopeCheck(x, y, m, b, tolerance)
        else false
    }

  def slopeCheck(x: Double, y: Double, m: Double, b: Double, tolerance: Double): Boolean = {
    // This is a slope comparison.. Any point on the line should have the same slope as the line.
    val m2: Double =
      if (x === 0) 0
      else (b - y) / (0 - x)

    val mDelta: Double =
      m - m2

    mDelta >= -tolerance && mDelta <= tolerance
  }

  def isFacingVertex(line: LineSegment, vertex: Vertex): Boolean =
    (line.normal dot Vertex.twoVerticesToVector2(vertex, line.center)) < 0

  // Centered at the origin
  def signedDistanceFunction(point: Vertex, start: Vertex, end: Vertex): Double = {
    val pa: Vertex = point - start
    val ba: Vertex = end - start
    val h: Double  = Math.min(1.0, Math.max(0.0, (pa.dot(ba) / ba.dot(ba))))
    (pa - (ba * h)).length
  }

}

sealed trait LineProperties
object LineProperties {
// y = mx + b
  final case class LineComponents(m: Double, b: Double) extends LineProperties
  case object ParallelToAxisY                           extends LineProperties
  case object InvalidLine                               extends LineProperties
}

sealed trait IntersectionResult {
  def toOption: Option[Vertex]
  def toList: List[Vertex]
}
object IntersectionResult {
  final case class IntersectionVertex(x: Double, y: Double) extends IntersectionResult {
    def toVertex: Vertex =
      Vertex(x, y)

    def toOption: Option[Vertex] =
      Some(toVertex)

    def toList: List[Vertex] =
      List(toVertex)
  }
  case object NoIntersection extends IntersectionResult {
    def toOption: Option[Vertex] = None
    def toList: List[Vertex]     = Nil
  }
}
