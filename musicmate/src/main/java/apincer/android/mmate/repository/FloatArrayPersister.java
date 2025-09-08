package apincer.android.mmate.repository;
import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.field.types.BaseDataType;
import com.j256.ormlite.support.DatabaseResults;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.sql.SQLException;

/**
 * ORMLite custom persister to save a float[] as a BYTE_ARRAY (BLOB) in the database.
 */
public class FloatArrayPersister extends BaseDataType {

    private static final FloatArrayPersister singleTon = new FloatArrayPersister();

    private FloatArrayPersister() {
        // We are storing this as a BLOB (byte array) in the database.
        // This persister can handle the float[] class.
        super(SqlType.BYTE_ARRAY, new Class<?>[] { float[].class });
    }

    public static FloatArrayPersister getSingleton() {
        return singleTon;
    }

    @Override
    public Object javaToSqlArg(FieldType fieldType, Object javaObject) {
        float[] floatArray = (float[]) javaObject;
        if (floatArray == null) {
            return null;
        }

        // 4 bytes per float
        ByteBuffer byteBuffer = ByteBuffer.allocate(floatArray.length * 4);
        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
        floatBuffer.put(floatArray);

        return byteBuffer.array();
    }

    @Override
    public Object sqlArgToJava(FieldType fieldType, Object sqlArg, int columnPos) {
        byte[] byteArray = (byte[]) sqlArg;
        if (byteArray == null || byteArray.length == 0) {
            return new float[0];
        }

        FloatBuffer floatBuffer = ByteBuffer.wrap(byteArray).asFloatBuffer();
        float[] floatArray = new float[floatBuffer.limit()];
        floatBuffer.get(floatArray);

        return floatArray;
    }

    // This is not needed for BYTE_ARRAY types
    @Override
    public Object parseDefaultString(FieldType fieldType, String defaultStr) {
        return null;
    }

    // This is not needed for BYTE_ARRAY types
    @Override
    public Object resultToSqlArg(FieldType fieldType, DatabaseResults results, int columnPos) throws SQLException {
        return results.getBytes(columnPos);
    }
}