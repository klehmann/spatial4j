package org.apache.lucene.spatial.base.jts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.StringTokenizer;

import org.apache.lucene.spatial.base.BBox;
import org.apache.lucene.spatial.base.Point;
import org.apache.lucene.spatial.base.Radius;
import org.apache.lucene.spatial.base.Shape;
import org.apache.lucene.spatial.base.ShapeIO;
import org.apache.lucene.spatial.base.exception.InvalidShapeException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.io.WKBWriter;
import com.vividsolutions.jts.io.WKTReader;

public class JTSShapeIO implements ShapeIO
{
  public GeometryFactory factory;

  public JTSShapeIO()
  {
    factory = new GeometryFactory();
  }

  public JTSShapeIO( GeometryFactory f )
  {
    factory = f;
  }

  public Shape readShape( String str ) throws InvalidShapeException
  {
    if( str.length() < 1 ) {
      throw new InvalidShapeException( str );
    }
    if( !Character.isLetter(str.charAt(0)) ) {
      StringTokenizer st = new StringTokenizer( str, " " );
      double p0 = Double.parseDouble( st.nextToken() );
      double p1 = Double.parseDouble( st.nextToken() );
      if( st.hasMoreTokens() ) {
        double p2 = Double.parseDouble( st.nextToken() );
        double p3 = Double.parseDouble( st.nextToken() );
        return new JtsEnvelope( new Envelope( p0, p2, p1, p3 ) );
      }
      return new JtsPoint2D( factory.createPoint(new Coordinate(p0, p1)) );
    }

    if( str.startsWith( "RADIUS(" ) ) {
      try {
        int idx = str.indexOf( '(' );
        int edx = str.lastIndexOf( ')' );
        StringTokenizer st = new StringTokenizer( str.substring(idx+1,edx), " " );
        double p0 = Double.parseDouble( st.nextToken() );
        double p1 = Double.parseDouble( st.nextToken() );
        double rr = Double.parseDouble( st.nextToken() );
        com.vividsolutions.jts.geom.Point p = factory.createPoint( new Coordinate( p0, p1 ) );
        return new JtsRadius2D( new JtsPoint2D(p), rr );
      }
      catch( Exception ex ) {
        throw new InvalidShapeException( "invalid radius: "+str, ex );
      }
    }

    WKTReader reader = new WKTReader(factory);
    try {
      Geometry geo = reader.read( str );
      if( geo instanceof com.vividsolutions.jts.geom.Point ) {
        return new JtsPoint2D((com.vividsolutions.jts.geom.Point)geo);
      }
      return new JtsGeometry( geo );
    }
    catch( com.vividsolutions.jts.io.ParseException ex ) {
      throw new InvalidShapeException( "error reading WKT", ex );
    }
  }

  private static final byte TYPE_POINT = 0;
  private static final byte TYPE_BBOX = 1;
  private static final byte TYPE_GEO = 2;
  private static final byte TYPE_RADIUS = 3;

  @Override
  public byte[] toBytes(Shape shape) throws IOException
  {
    if( shape instanceof Point ) {
      ByteBuffer bytes = ByteBuffer.wrap( new byte[1+(2*8)] );
      Point p = ((Point)shape);
      bytes.put( TYPE_POINT );
      bytes.putDouble( p.getX() );
      bytes.putDouble( p.getY() );
      return bytes.array();
    }

    if( shape instanceof BBox ) {
      BBox b = (BBox)shape;
      ByteBuffer bytes = ByteBuffer.wrap( new byte[1+(4*8)] );
      bytes.put( TYPE_BBOX );
      bytes.putDouble( b.getMinX() );
      bytes.putDouble( b.getMaxX() );
      bytes.putDouble( b.getMinY() );
      bytes.putDouble( b.getMaxY() );
      return bytes.array();
    }

    if( shape instanceof JtsGeometry ) {
      WKBWriter writer = new WKBWriter();
      byte[] bb = writer.write( ((JtsGeometry)shape).geo );
      ByteBuffer bytes = ByteBuffer.wrap( new byte[1+bb.length] );
      bytes.put( TYPE_GEO );
      bytes.put( bb );
      return bytes.array();
    }

    if( shape instanceof Radius ) {
      Radius p = ((Radius)shape);
      ByteBuffer bytes = ByteBuffer.wrap( new byte[1+(4*8)] );
      bytes.put( TYPE_RADIUS );
      bytes.putDouble( p.getPoint().getX() );
      bytes.putDouble( p.getPoint().getY() );
      bytes.putDouble( p.getRadius() );
      return bytes.array();
    }

    throw new IllegalArgumentException("unsuported shape:"+shape );
  }

  @Override
  public Shape readShape(byte[] array, int offset, int length) throws InvalidShapeException
  {
    ByteBuffer bytes = ByteBuffer.wrap(array, offset, length);
    byte type = bytes.get();
    if( type == TYPE_POINT ) {
      return new JtsPoint2D( factory.createPoint(
          new Coordinate(bytes.getDouble(),bytes.getDouble())) );
    }
    else if( type == TYPE_BBOX ) {
      return new JtsEnvelope(
          bytes.getDouble(),bytes.getDouble(),
          bytes.getDouble(),bytes.getDouble());
    }
    else if( type == TYPE_GEO ) {

    }
    else if( type == TYPE_RADIUS ) {
      return new JtsRadius2D( new JtsPoint2D( factory.createPoint(
          new Coordinate(bytes.getDouble(),bytes.getDouble())) ), bytes.getDouble() );
    }
    throw new InvalidShapeException( "shape not handled: "+type );
  }

  @Override
  public String toString(Shape shape)
  {
    if( shape instanceof org.apache.lucene.spatial.base.Point ) {
      NumberFormat nf = NumberFormat.getInstance( Locale.US );
      nf.setGroupingUsed( false );
      nf.setMaximumFractionDigits( 6 );
      nf.setMinimumFractionDigits( 6 );
      org.apache.lucene.spatial.base.Point point = (org.apache.lucene.spatial.base.Point)shape;
      return nf.format( point.getX() ) + " " + nf.format( point.getY() );
    }
    else if( shape instanceof BBox ) {
      BBox bbox = (BBox)shape;
      NumberFormat nf = NumberFormat.getInstance( Locale.US );
      nf.setGroupingUsed( false );
      nf.setMaximumFractionDigits( 6 );
      nf.setMinimumFractionDigits( 6 );
      return
        nf.format( bbox.getMinX() ) + " " +
        nf.format( bbox.getMinY() ) + " " +
        nf.format( bbox.getMaxX() ) + " " +
        nf.format( bbox.getMaxY() );
    }
    else if( shape instanceof JtsGeometry ) {
      JtsGeometry geo = (JtsGeometry)shape;
      return geo.geo.toText();
    }
    return shape.toString();
  }
}