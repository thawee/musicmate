package apincer.music.core.utils;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class ReflectUtil {
    public static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                fields.add(field);
            }
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    /***************************************
     * Returns the value of a particular field in an object. If the field or
     * it's class is not public this method will try to make it accessible by
     * invoking {@link AccessibleObject#setAccessible(boolean)} on it. This
     * requires that the caller's context has sufficient rights to do so, else a
     * {@link SecurityException} will be thrown.
     *
     * @param  rField  The field to return the value of
     * @param  rObject The object to read the field value from
     *
     * @return The value of the given field
     *
     * @throws IllegalArgumentException If the field doesn't exist or accessing
     *                                  the field value fails
     */
    public static Object getFieldValue(Field rField, Object rObject)
    {
        if (rField != null)
        {
            try
            {
                return checkAccessible(rField).get(rObject);
            }
            catch (Exception e)
            {
                throw new IllegalArgumentException("Field access failed: " +
                        rObject.getClass() + "." +
                        rField,
                        e);
            }
        }
        else
        {
            throw new IllegalArgumentException("Invalid field: " + rField);
        }
    }

    /***************************************
     * Checks whether a class member is publicly accessible. If not the method
     * {@link AccessibleObject#setAccessible(boolean)} will be invoked on it to
     * make it accessible.
     *
     * @param  rMember The member to check
     *
     * @return The input member to allow call concatenation
     */
    public static <T extends Member> T checkAccessible(T rMember)
    {
        if (rMember instanceof AccessibleObject &&
                !((AccessibleObject) rMember).isAccessible())
        {
            Class<?> rMemberClass = rMember.getDeclaringClass();

            if (!Modifier.isPublic(rMember.getModifiers()) ||
                    !Modifier.isPublic(rMemberClass.getModifiers()))
            {
                ((AccessibleObject) rMember).setAccessible(true);
            }
        }

        return rMember;
    }
}
