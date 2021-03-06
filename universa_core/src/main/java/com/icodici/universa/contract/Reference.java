package com.icodici.universa.contract;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.roles.Role;
import com.icodici.crypto.KeyAddress;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node2.Config;
import net.sergeych.biserializer.*;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Base64u;
import net.sergeych.utils.Bytes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.icodici.universa.contract.Reference.conditionsModeType.all_of;
import static com.icodici.universa.contract.Reference.conditionsModeType.any_of;

@BiType(name = "Reference")
public class Reference implements BiSerializable {

    public enum conditionsModeType {
        all_of,
        any_of,
        simple_condition
    }

    public String name = "";
    public int type = TYPE_EXISTING_DEFINITION;
    public String transactional_id = "";
    public HashId contract_id = null;
    public boolean required = true;
    public HashId origin = null;
    public List<Role> signed_by = new ArrayList<>();
    public List<String> fields = new ArrayList<>();
    public List<String> roles = new ArrayList<>();
    public List<Approvable> matchingItems = new ArrayList<>();
    private Binder conditions = new Binder();
    private Contract baseContract;
    private String comment = null;

    public static final int TYPE_TRANSACTIONAL = 1;
    public static final int TYPE_EXISTING_DEFINITION = 2;
    public static final int TYPE_EXISTING_STATE = 3;

    public Reference() {}

    /**
     *adds a basic contract for reference
     *
     *@param contract basic contract.
     */
    public Reference(Contract contract) {
        baseContract = contract;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        this.name = data.getString("name", null);

        this.type = data.getInt("type", null);
        this.comment = data.getString("comment", null);

        this.transactional_id = data.getString("transactional_id", "");

//        String str_contract_id = data.getString("contract_id", null);
//        if (str_contract_id != null)
//            this.contract_id = HashId.withDigest(Bytes.fromHex(str_contract_id).getData());
//        else
//            this.contract_id = null;
//
//        this.required = data.getBoolean("required", true);
//
//        String str_origin = data.getString("origin", null);
//        if (str_origin != null)
//            this.origin = HashId.withDigest(Bytes.fromHex(str_origin).getData());
//        else
//            this.origin = null;

        this.contract_id = deserializer.deserialize(data.get("contract_id"));
        this.origin = deserializer.deserialize(data.get("origin"));

        this.signed_by = deserializer.deserializeCollection(data.getList("signed_by", new ArrayList<>()));


        List<String> roles = data.getList("roles", null);
        if (roles != null) {
            this.roles.clear();
            roles.forEach(this::addRole);
        }

        List<String> fields = data.getList("fields", null);
        if (fields != null) {
            this.fields.clear();
            fields.forEach(this::addField);
        }

        conditions = data.getBinder("where");
    }

    @Override
    public Binder serialize(BiSerializer s) {
        Binder data = new Binder();
        data.set("name", s.serialize(this.name));
        data.set("type", s.serialize(this.type));
        data.set("transactional_id", s.serialize(this.transactional_id));
//        if (this.contract_id != null)
//            data.set("contract_id", s.serialize(Bytes.toHex(this.contract_id.getDigest())));
//        data.set("required", s.serialize(this.required));
//        if (this.origin != null)
//            data.set("origin", s.serialize(Bytes.toHex(this.origin.getDigest())));
        if (this.contract_id != null)
            data.set("contract_id", s.serialize(this.contract_id));
        data.set("required", s.serialize(this.required));
        if (this.origin != null)
            data.set("origin", s.serialize(this.origin));
        data.set("signed_by", s.serialize(signed_by));

        data.set("roles", s.serialize(this.roles));
        data.set("fields", s.serialize(this.fields));

        data.set("where", s.serialize(this.conditions));

        if (comment != null)
            data.set("comment", comment);

        return data;
    }

    static public Reference fromDslBinder(Binder ref, Contract contract) {
        String name = ref.getString("name");
        String comment = ref.getString("comment", null);
        Binder where = null;
        try {
            where = ref.getBinderOrThrow("where");
        }
        catch (Exception e)
        {
            // Insert simple condition to binder with key all_of
            List<String> simpleConditions = ref.getList("where", null);
            if (simpleConditions != null)
                where = new Binder(all_of.name(), simpleConditions);
        }

        Reference reference = new Reference(contract);

        if (name == null)
            throw new IllegalArgumentException("Expected reference name");

        reference.setName(name);
        reference.setComment(comment);

        if (where != null)
            reference.setConditions(where);

        return reference;
    }

    public boolean equals(Reference a) {
        Binder dataThis = serialize(new BiSerializer());
        Binder dataA = a.serialize(new BiSerializer());
        return dataThis.equals(dataA);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    //Operators
    final static String[] operators = {" defined"," undefined","<=",">=","<",">","!=","=="," matches "," is_a "," is_inherit ","inherits ","inherit "," can_play "};

    final static int DEFINED = 0;
    final static int UNDEFINED = 1;
    final static int LESS_OR_EQUAL = 2;
    final static int MORE_OR_EQUAL = 3;
    final static int LESS = 4;
    final static int MORE = 5;
    final static int NOT_EQUAL = 6;
    final static int EQUAL = 7;
    final static int MATCHES = 8;
    final static int IS_A = 9;
    final static int IS_INHERIT = 10;
    final static int INHERITS = 11;
    final static int INHERIT = 12;
    final static int CAN_PLAY = 13;

    //Conversions
    final static int NO_CONVERSION = 0;
    final static int CONVERSION_BIG_DECIMAL = 1;  // ::number

    enum compareOperandType {
        FIELD,
        CONSTSTR,
        CONSTOTHER
    }

    private boolean isObjectMayCastToDouble(Object obj) throws Exception {
        return obj.getClass().getName().endsWith("Float") || obj.getClass().getName().endsWith("Double");
    }

    private boolean isObjectMayCastToLong(Object obj) throws Exception {
        return obj.getClass().getName().endsWith("Byte") || obj.getClass().getName().endsWith("Short") ||
               obj.getClass().getName().endsWith("Integer") || obj.getClass().getName().endsWith("Long");
    }

    private double objectCastToDouble(Object obj) throws Exception {
        double val;

        if (obj.getClass().getName().endsWith("Float") ||
            obj.getClass().getName().endsWith("Double"))
            val = (double) obj;
        else
            throw new IllegalArgumentException("Expected floating point number operand in condition.");

        return val;
    }

    private long objectCastToLong(Object obj) throws Exception {
        long val;

        if (obj.getClass().getName().endsWith("Byte"))
            val = (byte) obj;
        else if (obj.getClass().getName().endsWith("Short"))
            val = (short) obj;
        else if (obj.getClass().getName().endsWith("Integer"))
            val = (int) obj;
        else if (obj.getClass().getName().endsWith("Long"))
            val = (long) obj;
        else
            throw new IllegalArgumentException("Expected number operand in condition.");

        return val;
    }

    private long objectCastToTimeSeconds(Object obj, String operand, compareOperandType typeOfOperand) throws Exception {
        long val;

        if ((obj == null) && (typeOfOperand == compareOperandType.FIELD))
            throw new IllegalArgumentException("Error getting operand: " + operand);

        if ((obj != null) && obj.getClass().getName().endsWith("ZonedDateTime"))
            val = ((ZonedDateTime) obj).toEpochSecond();
        else if ((obj != null) && obj.getClass().getName().endsWith("String"))
            val = ZonedDateTime.parse((String) obj, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"))).toEpochSecond();
        else if ((obj != null) && isObjectMayCastToLong(obj))
            val = objectCastToLong(obj);
        else if (typeOfOperand == compareOperandType.CONSTSTR)
            val = ZonedDateTime.parse(operand, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"))).toEpochSecond();
        else if (typeOfOperand == compareOperandType.CONSTOTHER)
            val = Long.parseLong(operand);
        else
            throw new IllegalArgumentException("Error parsing DateTime from operand: " + operand);

        return val;
    }

    private BigDecimal objectCastToBigDecimal(Object obj, String operand, compareOperandType typeOfOperand) throws Exception {
        BigDecimal val;

        if ((obj == null) && (typeOfOperand == compareOperandType.FIELD))
            throw new IllegalArgumentException("Error getting operand: " + operand);

        if ((obj != null) && obj.getClass().getName().endsWith("String"))
            val = new BigDecimal((String) obj);
        else if ((obj != null) && isObjectMayCastToLong(obj))
            val = new BigDecimal(objectCastToLong(obj));
        else if ((typeOfOperand == compareOperandType.CONSTSTR) || (typeOfOperand == compareOperandType.CONSTOTHER))
            val = new BigDecimal(operand);
        else
            throw new IllegalArgumentException("Error parsing DateTime from operand: " + operand);

        return val;
    }

    /**
     *The comparison method for finding reference contract
     *
     * @param refContract contract to check for matching
     * @param leftOperand field_selector
     * @param rightOperand right operand  (constant | field_selector), constant = ("null" | number | string | true | false)
     * @param typeOfRightOperand type of left operand (constant | field_selector), constant = ("null" | number | string | true | false)
     * @param typeOfRightOperand type of right operand (constant | field_selector), constant = ("null" | number | string | true | false)
     * @param indxOperator index operator in array of operators
     * @param contracts contract list to check for matching
     * @param iteration check inside references iteration number
     * @return true if match or false
     */
    private boolean compareOperands(Contract refContract,
                                   String leftOperand,
                                   String rightOperand,
                                   compareOperandType typeOfLeftOperand,
                                   compareOperandType typeOfRightOperand,
                                   boolean isBigDecimalConversion,
                                   int indxOperator,
                                   Collection<Contract> contracts,
                                   int iteration)
    {
        boolean ret = false;
        Contract leftOperandContract = null;
        Contract rightOperandContract = null;
        Object left = null;
        Object right = null;
        double leftValD = 0;
        double rightValD = 0;
        long leftValL = 0;
        long rightValL = 0;
        BigDecimal leftBigDecimal;
        BigDecimal rightBigDecimal;
        boolean isLeftDouble = false;
        boolean isRightDouble = false;
        int firstPointPos;

        if (leftOperand != null) {
            if (typeOfLeftOperand == compareOperandType.FIELD) {
                if (leftOperand.startsWith("ref.")) {
                    leftOperand = leftOperand.substring(4);
                    leftOperandContract = refContract;
                } else if (leftOperand.startsWith("this.")) {
                    if (baseContract == null)
                        throw new IllegalArgumentException("Use left operand in condition: " + leftOperand + ". But this contract not initialized.");

                    leftOperand = leftOperand.substring(5);
                    leftOperandContract = baseContract;
                } else if ((firstPointPos = leftOperand.indexOf(".")) > 0) {
                    if (baseContract == null)
                        throw new IllegalArgumentException("Use left operand in condition: " + leftOperand + ". But this contract not initialized.");

                    Reference ref = baseContract.findReferenceByName(leftOperand.substring(0, firstPointPos));
                    if (ref == null)
                        throw new IllegalArgumentException("Not found reference: " + leftOperand.substring(0, firstPointPos));

                    for (Contract checkedContract : contracts)
                        if (ref.isMatchingWith(checkedContract, contracts, iteration + 1))
                            leftOperandContract = checkedContract;

                    if (leftOperandContract == null)
                        return false;

                    leftOperand = leftOperand.substring(firstPointPos + 1);
                } else
                    throw new IllegalArgumentException("Invalid format of left operand in condition: " + leftOperand + ". Missing contract field.");
            } else if (typeOfLeftOperand == compareOperandType.CONSTOTHER) {
                if (indxOperator == CAN_PLAY) {
                    if (leftOperand.equals("ref")) {
                        leftOperandContract = refContract;
                    } else if (leftOperand.equals("this")) {
                        if (baseContract == null)
                            throw new IllegalArgumentException("Use left operand in condition: " + leftOperand + ". But this contract not initialized.");

                        leftOperandContract = baseContract;
                    } else {
                        if (baseContract == null)
                            throw new IllegalArgumentException("Use left operand in condition: " + leftOperand + ". But this contract not initialized.");

                        Reference ref = baseContract.findReferenceByName(leftOperand);
                        if (ref == null)
                            throw new IllegalArgumentException("Not found reference: " + leftOperand);

                        for (Contract checkedContract : contracts)
                            if (ref.isMatchingWith(checkedContract, contracts, iteration + 1))
                                leftOperandContract = checkedContract;

                        if (leftOperandContract == null)
                            return false;
                    }
                } else if (leftOperand.equals("now"))
                    left = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
            }
        }

        if (rightOperand != null) {     // if != null, rightOperand then FIELD or CONSTANT
            if (typeOfRightOperand == compareOperandType.FIELD) {     // if typeOfRightOperand - FIELD
                if (rightOperand.startsWith("ref.")) {
                    rightOperand = rightOperand.substring(4);
                    rightOperandContract = refContract;
                } else if (rightOperand.startsWith("this.")) {
                    if (baseContract == null)
                        throw new IllegalArgumentException("Use right operand in condition: " + rightOperand + ". But this contract not initialized.");

                    rightOperand = rightOperand.substring(5);
                    rightOperandContract = baseContract;
                } else if ((firstPointPos = rightOperand.indexOf(".")) > 0) {
                    if (baseContract == null)
                        throw new IllegalArgumentException("Use right operand in condition: " + rightOperand + ". But this contract not initialized.");

                    Reference ref = baseContract.findReferenceByName(rightOperand.substring(0, firstPointPos));
                    if (ref == null)
                        throw new IllegalArgumentException("Not found reference: " + rightOperand.substring(0, firstPointPos));

                    for (Contract checkedContract: contracts)
                        if (ref.isMatchingWith(checkedContract, contracts, iteration + 1))
                            rightOperandContract = checkedContract;

                    if (rightOperandContract == null)
                        return false;

                    rightOperand = rightOperand.substring(firstPointPos + 1);
                }
                else
                    throw new IllegalArgumentException("Invalid format of right operand in condition: " + rightOperand + ". Missing contract field.");
            } else if (typeOfRightOperand == compareOperandType.CONSTOTHER) {
                if (rightOperand.equals("now"))
                    right = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
            }

            if ((leftOperandContract != null) && (indxOperator != CAN_PLAY))
                left = leftOperandContract.get(leftOperand);
            if (rightOperandContract != null)
                right = rightOperandContract.get(rightOperand);

            try {
                switch (indxOperator) {
                    case LESS:
                    case MORE:
                    case LESS_OR_EQUAL:
                    case MORE_OR_EQUAL:
                        if (typeOfLeftOperand == compareOperandType.FIELD && left == null)
                            break;

                        if (typeOfRightOperand == compareOperandType.FIELD && right == null)
                            break;

                        if (isBigDecimalConversion) {
                            leftBigDecimal = objectCastToBigDecimal(left, leftOperand, typeOfLeftOperand);
                            rightBigDecimal = objectCastToBigDecimal(right, rightOperand, typeOfRightOperand);

                            if (((indxOperator == LESS) && (leftBigDecimal.compareTo(rightBigDecimal) == -1)) ||
                                ((indxOperator == MORE) && (leftBigDecimal.compareTo(rightBigDecimal) == 1)) ||
                                ((indxOperator == LESS_OR_EQUAL) && (leftBigDecimal.compareTo(rightBigDecimal) < 1)) ||
                                ((indxOperator == MORE_OR_EQUAL) && (leftBigDecimal.compareTo(rightBigDecimal) > -1)))
                                ret = true;
                        } else if (((left != null) && left.getClass().getName().endsWith("ZonedDateTime")) ||
                                  ((right != null) && right.getClass().getName().endsWith("ZonedDateTime"))) {
                            long leftTime = objectCastToTimeSeconds(left, leftOperand, typeOfLeftOperand);
                            long rightTime = objectCastToTimeSeconds(right, rightOperand, typeOfRightOperand);

                            if (((indxOperator == LESS) && (leftTime < rightTime)) ||
                                ((indxOperator == MORE) && (leftTime > rightTime)) ||
                                ((indxOperator == LESS_OR_EQUAL) && (leftTime <= rightTime)) ||
                                ((indxOperator == MORE_OR_EQUAL) && (leftTime >= rightTime)))
                                ret = true;
                        } else {
                            if ((typeOfLeftOperand == compareOperandType.FIELD) && (left != null)) {
                                if (isLeftDouble = isObjectMayCastToDouble(left))
                                    leftValD = objectCastToDouble(left);
                                else
                                    leftValL = objectCastToLong(left);
                            }

                            if ((typeOfRightOperand == compareOperandType.FIELD) && (right != null)) {
                                if (isRightDouble = isObjectMayCastToDouble(right))
                                    rightValD = objectCastToDouble(right);
                                else
                                    rightValL = objectCastToLong(right);
                            }

                            if ((typeOfLeftOperand == compareOperandType.FIELD) && (typeOfRightOperand == compareOperandType.FIELD)) {
                                if (((indxOperator == LESS) && ((isLeftDouble ? leftValD : leftValL) < (isRightDouble ? rightValD : rightValL))) ||
                                    ((indxOperator == MORE) && ((isLeftDouble ? leftValD : leftValL) > (isRightDouble ? rightValD : rightValL))) ||
                                    ((indxOperator == LESS_OR_EQUAL) && ((isLeftDouble ? leftValD : leftValL) <= (isRightDouble ? rightValD : rightValL))) ||
                                    ((indxOperator == MORE_OR_EQUAL) && ((isLeftDouble ? leftValD : leftValL) >= (isRightDouble ? rightValD : rightValL))))
                                    ret = true;
                            } else if ((typeOfLeftOperand == compareOperandType.FIELD) && (typeOfRightOperand == compareOperandType.CONSTOTHER)) { // rightOperand is CONSTANT (null | number | true | false)
                                if ((rightOperand != "null") && (rightOperand != "false") && (rightOperand != "true"))
                                    if ((rightOperand.contains(".") &&
                                        (((indxOperator == LESS) && ((isLeftDouble ? leftValD : leftValL) < Double.parseDouble(rightOperand))) ||
                                        ((indxOperator == MORE) && ((isLeftDouble ? leftValD : leftValL) > Double.parseDouble(rightOperand))) ||
                                        ((indxOperator == LESS_OR_EQUAL) && ((isLeftDouble ? leftValD : leftValL) <= Double.parseDouble(rightOperand))) ||
                                        ((indxOperator == MORE_OR_EQUAL) && ((isLeftDouble ? leftValD : leftValL) >= Double.parseDouble(rightOperand))))) ||
                                        (!rightOperand.contains(".") &&
                                        (((indxOperator == LESS) && ((isLeftDouble ? leftValD : leftValL) < Long.parseLong(rightOperand))) ||
                                        ((indxOperator == MORE) && ((isLeftDouble ? leftValD : leftValL) > Long.parseLong(rightOperand))) ||
                                        ((indxOperator == LESS_OR_EQUAL) && ((isLeftDouble ? leftValD : leftValL) <= Long.parseLong(rightOperand))) ||
                                        ((indxOperator == MORE_OR_EQUAL) && ((isLeftDouble ? leftValD : leftValL) >= Long.parseLong(rightOperand))))))
                                        ret = true;
                            } else if ((typeOfRightOperand == compareOperandType.FIELD) && (typeOfLeftOperand == compareOperandType.CONSTOTHER)) { // leftOperand is CONSTANT (null | number | true | false)
                                if ((leftOperand != "null") && (leftOperand != "false") && (leftOperand != "true"))
                                    if ((leftOperand.contains(".") &&
                                        (((indxOperator == LESS) && (Double.parseDouble(leftOperand) < (isRightDouble ? rightValD : rightValL))) ||
                                        ((indxOperator == MORE) && (Double.parseDouble(leftOperand) > (isRightDouble ? rightValD : rightValL))) ||
                                        ((indxOperator == LESS_OR_EQUAL) && (Double.parseDouble(leftOperand) <= (isRightDouble ? rightValD : rightValL))) ||
                                        ((indxOperator == MORE_OR_EQUAL) && (Double.parseDouble(leftOperand) >= (isRightDouble ? rightValD : rightValL))))) ||
                                        (!leftOperand.contains(".") &&
                                        (((indxOperator == LESS) && (Long.parseLong(leftOperand) < (isRightDouble ? rightValD : rightValL))) ||
                                        ((indxOperator == MORE) && (Long.parseLong(leftOperand) > (isRightDouble ? rightValD : rightValL))) ||
                                        ((indxOperator == LESS_OR_EQUAL) && (Long.parseLong(leftOperand) <= (isRightDouble ? rightValD : rightValL))) ||
                                        ((indxOperator == MORE_OR_EQUAL) && (Long.parseLong(leftOperand) >= (isRightDouble ? rightValD : rightValL))))))
                                        ret = true;
                            } else
                                throw new IllegalArgumentException("Invalid operator in condition for string: " + operators[indxOperator]);
                        }

                        break;

                    case NOT_EQUAL:
                    case EQUAL:
                        if (typeOfLeftOperand == compareOperandType.FIELD && left == null && !rightOperand.equals("null"))
                            break;

                        if (typeOfRightOperand == compareOperandType.FIELD && right == null && !leftOperand.equals("null"))
                            break;

                        if (isBigDecimalConversion) {
                            leftBigDecimal = objectCastToBigDecimal(left, leftOperand, typeOfLeftOperand);
                            rightBigDecimal = objectCastToBigDecimal(right, rightOperand, typeOfRightOperand);

                            if (((indxOperator == EQUAL) && (leftBigDecimal.compareTo(rightBigDecimal) == 0)) ||
                                ((indxOperator == NOT_EQUAL) && (leftBigDecimal.compareTo(rightBigDecimal) != 0)))
                                ret = true;
                        } else if (((left != null) && left.getClass().getName().endsWith("HashId")) ||
                            ((right != null) && right.getClass().getName().endsWith("HashId"))) {
                            HashId leftID;
                            HashId rightID;

                            if ((left != null) && left.getClass().getName().endsWith("HashId"))
                                leftID = (HashId) left;
                            else if ((left != null) && left.getClass().getName().endsWith("String"))
                                leftID = HashId.withDigest((String) left);
                            else
                                leftID = HashId.withDigest(leftOperand);

                            if ((right != null) && right.getClass().getName().endsWith("HashId"))
                                rightID = (HashId) right;
                            else if ((right != null) && right.getClass().getName().endsWith("String"))
                                rightID = HashId.withDigest((String) right);
                            else
                                rightID = HashId.withDigest(rightOperand);

                            ret = leftID.equals(rightID);

                            if (indxOperator == NOT_EQUAL)
                                ret = !ret;
                        } else if (((left != null) && (left.getClass().getName().endsWith("Role") || left.getClass().getName().endsWith("RoleLink"))) ||
                                   ((right != null) && (right.getClass().getName().endsWith("Role") || right.getClass().getName().endsWith("RoleLink")))) { // if role - compare with role, key or address
                            if (((left != null) && (left.getClass().getName().endsWith("Role") || left.getClass().getName().endsWith("RoleLink"))) &&
                                ((right != null) && (right.getClass().getName().endsWith("Role") || right.getClass().getName().endsWith("RoleLink")))) {
                                if (((indxOperator == NOT_EQUAL) && !((Role)left).equalsIgnoreName((Role) right)) ||
                                    ((indxOperator == EQUAL) && ((Role)left).equalsIgnoreName((Role) right)))
                                    ret = true;

                            } else {
                                Role role;
                                String compareOperand;
                                if ((left != null) && (left.getClass().getName().endsWith("Role") || left.getClass().getName().endsWith("RoleLink"))) {
                                    role = (Role) left;
                                    if ((right != null) && (right.getClass().getName().endsWith("String")))
                                        compareOperand = (String) right;
                                    else
                                        compareOperand = rightOperand;
                                } else {
                                    role = (Role) right;
                                    if ((left != null) && (left.getClass().getName().endsWith("String")))
                                        compareOperand = (String) left;
                                    else
                                        compareOperand = leftOperand;
                                }

                                if(role instanceof RoleLink) {
                                    role  = role.resolve();
                                }

                                try {
                                    compareOperand = compareOperand.replaceAll("\\s+", "");       // for key in quotes

                                    if (compareOperand.length() > 72) {
                                        // Key
                                        PublicKey publicKey = new PublicKey(Base64u.decodeCompactString(compareOperand));
                                        SimpleRole simpleRole = new SimpleRole(role.getName(), Do.listOf(publicKey));
                                        ret = role.equalsIgnoreName(simpleRole);
                                    } else {
                                        // Address
                                        KeyAddress ka = new KeyAddress(compareOperand);
                                        SimpleRole simpleRole = new SimpleRole(role.getName(), Do.listOf(ka));
                                        ret = role.equalsIgnoreName(simpleRole);
                                    }
                                }
                                catch (Exception e) {
                                    throw new IllegalArgumentException("Key or address compare error in condition: " + e.getMessage());
                                }

                                if (indxOperator == NOT_EQUAL)
                                    ret = !ret;

                            }
                        } else if (((left != null) && left.getClass().getName().endsWith("ZonedDateTime")) ||
                                   ((right != null) && right.getClass().getName().endsWith("ZonedDateTime"))) {
                            long leftTime = objectCastToTimeSeconds(left, leftOperand, typeOfLeftOperand);
                            long rightTime = objectCastToTimeSeconds(right, rightOperand, typeOfRightOperand);

                            if (((indxOperator == NOT_EQUAL) && (leftTime != rightTime)) ||
                                ((indxOperator == EQUAL) && (leftTime == rightTime)))
                                ret = true;
                        } else if ((typeOfLeftOperand == compareOperandType.FIELD) && (typeOfRightOperand == compareOperandType.FIELD)) {   // operands is FIELDs
                            if ((left != null) && (right != null)) {
                                boolean isNumbers = true;

                                if (isLeftDouble = isObjectMayCastToDouble(left))
                                    leftValD = objectCastToDouble(left);
                                else if (isObjectMayCastToLong(left))
                                    leftValL = objectCastToLong(left);
                                else
                                    isNumbers = false;

                                if (isNumbers) {
                                    if (isRightDouble = isObjectMayCastToDouble(right))
                                        rightValD = objectCastToDouble(right);
                                    else if (isObjectMayCastToLong(right))
                                        rightValL = objectCastToLong(right);
                                    else
                                        isNumbers = false;
                                }

                                if (isNumbers && ((isLeftDouble && !isRightDouble) || (!isLeftDouble && isRightDouble))) {
                                    if (((indxOperator == NOT_EQUAL) && ((isLeftDouble ? leftValD : leftValL) != (isRightDouble ? rightValD : rightValL))) ||
                                            ((indxOperator == EQUAL) && ((isLeftDouble ? leftValD : leftValL) == (isRightDouble ? rightValD : rightValL))))
                                        ret = true;
                                }
                                else if (((indxOperator == NOT_EQUAL) && !left.equals(right)) ||
                                         ((indxOperator == EQUAL) && left.equals(right)))
                                    ret = true;
                            }
                        } else {
                            Object field;
                            String compareOperand;
                            compareOperandType typeCompareOperand;
                            if (typeOfLeftOperand == compareOperandType.FIELD) {
                                field = left;
                                compareOperand = rightOperand;
                                typeCompareOperand = typeOfRightOperand;
                            }
                            else if (typeOfRightOperand == compareOperandType.FIELD) {
                                field = right;
                                compareOperand = leftOperand;
                                typeCompareOperand = typeOfLeftOperand;
                            }
                            else
                                throw new IllegalArgumentException("At least one operand must be a field");

                            if (typeCompareOperand == compareOperandType.CONSTOTHER) {         // compareOperand is CONSTANT (null|number|true|false)
                                if (!compareOperand.equals("null") && !compareOperand.equals("false") && !compareOperand.equals("true")) {
                                    if (field != null)
                                    {
                                        if (isObjectMayCastToDouble(field)) {
                                            leftValD = objectCastToDouble(field);
                                            Double leftDouble = new Double(leftValD);

                                            if ((compareOperand.contains(".") &&
                                                (((indxOperator == NOT_EQUAL) && !leftDouble.equals(Double.parseDouble(compareOperand))) ||
                                                 ((indxOperator == EQUAL) && leftDouble.equals(Double.parseDouble(compareOperand))))) ||
                                                (!compareOperand.contains(".") &&
                                                 (((indxOperator == NOT_EQUAL) && (leftValD != Long.parseLong(compareOperand))) ||
                                                  ((indxOperator == EQUAL) && (leftValD == Long.parseLong(compareOperand))))))
                                                ret = true;
                                        } else {
                                            leftValL = objectCastToLong(field);
                                            Long leftLong = new Long(leftValL);

                                            if ((!compareOperand.contains(".") &&
                                                (((indxOperator == NOT_EQUAL) && !leftLong.equals(Long.parseLong(compareOperand))) ||
                                                 ((indxOperator == EQUAL) && leftLong.equals(Long.parseLong(compareOperand))))) ||
                                                (compareOperand.contains(".") &&
                                                 (((indxOperator == NOT_EQUAL) && (leftValL != Double.parseDouble(compareOperand))) ||
                                                  ((indxOperator == EQUAL) && (leftValL == Double.parseDouble(compareOperand))))))
                                                ret = true;
                                        }
                                    }
                                } else {          // if compareOperand : null|false|true
                                    if (((indxOperator == NOT_EQUAL) &&
                                        ((compareOperand.equals("null") && (field != null)) ||
                                         (compareOperand.equals("true") && ((field != null) && !(boolean) field)) ||
                                         (compareOperand.equals("false") && ((field != null) && (boolean) field))))
                                        || ((indxOperator == EQUAL) &&
                                        ((compareOperand.equals("null") && (field == null)) ||
                                         (compareOperand.equals("true") && ((field != null) && (boolean) field)) ||
                                         (compareOperand.equals("false") && ((field != null) && !(boolean) field)))))
                                        ret = true;
                                }
                            } else if (typeCompareOperand == compareOperandType.CONSTSTR) {          // compareOperand is CONSTANT (string)
                                 if ((field != null) &&
                                     (((indxOperator == NOT_EQUAL) && !field.equals(compareOperand)) ||
                                      ((indxOperator == EQUAL) && field.equals(compareOperand))))
                                    ret = true;
                            }
                            else
                                throw new IllegalArgumentException("Invalid type of operand: " + compareOperand);
                        }

                        break;

                    case MATCHES:

                        break;
                    case IS_INHERIT:
                        // deprecate warning
                        System.out.println("WARNING: Operator 'is_inherit' was deprecated. Use operator 'is_a'.");
                    case IS_A:
                        if ((left == null) || !left.getClass().getName().endsWith("Reference"))
                            throw new IllegalArgumentException("Expected reference in condition in left operand: " + leftOperand);

                        if ((right == null) || !right.getClass().getName().endsWith("Reference"))
                            throw new IllegalArgumentException("Expected reference in condition in right operand: " + rightOperand);

                        ret = ((Reference) left).isInherited((Reference) right, refContract, contracts, iteration + 1);

                        break;
                    case INHERIT:
                        // deprecate warning
                        System.out.println("WARNING: Operator 'inherit' was deprecated. Use operator 'inherits'.");
                    case INHERITS:
                        if ((right == null) || !right.getClass().getName().endsWith("Reference"))
                            throw new IllegalArgumentException("Expected reference in condition in right operand: " + rightOperand);

                        ret = ((Reference) right).isMatchingWith(refContract, contracts, iteration + 1);

                        break;
                    case CAN_PLAY:
                        if ((right == null) || !(right.getClass().getName().endsWith("Role") || right.getClass().getName().endsWith("RoleLink")))
                            throw new IllegalArgumentException("Expected role in condition in right operand: " + rightOperand);

                        Set<PublicKey> keys;
                        if (leftOperand.equals("this"))
                            keys = leftOperandContract.getEffectiveKeys();
                        else
                            keys = leftOperandContract.getSealedByKeys();

                        ret = ((Role) right).isAllowedForKeys(keys);

                        break;
                    default:
                        throw new IllegalArgumentException("Invalid operator in condition");
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                throw new IllegalArgumentException("Error compare operands in condition: " + e.getMessage());
            }
        } else {       // if rightOperand == null, then operation: defined / undefined
            if (indxOperator == DEFINED) {
                try {
                    if (leftOperandContract.get(leftOperand) != null)
                        ret = true;
                } catch (Exception e) {}
            } else if (indxOperator == UNDEFINED) {
                try {
                    ret = (leftOperandContract.get(leftOperand) == null);
                }
                catch (Exception e) {
                    ret = true;
                }
            }
            else
                throw new IllegalArgumentException("Invalid operator in condition");
        }

        return ret;
    }

    private Binder packCondition(int operator,
                                 String leftOperand,
                                 String rightOperand,
                                 compareOperandType typeOfLeftOperand,
                                 compareOperandType typeOfRightOperand,
                                 int leftConversion,
                                 int rightConversion) {
        Binder packedCondition = new Binder();

        packedCondition.set("operator", operator);
        packedCondition.set("leftOperand", leftOperand);
        packedCondition.set("rightOperand", rightOperand);

        if (typeOfLeftOperand == Reference.compareOperandType.FIELD)
            packedCondition.set("typeOfLeftOperand", 0);
        else if (typeOfLeftOperand == Reference.compareOperandType.CONSTSTR)
            packedCondition.set("typeOfLeftOperand", 1);
        else
            packedCondition.set("typeOfLeftOperand", 2);

        if (typeOfRightOperand == Reference.compareOperandType.FIELD)
            packedCondition.set("typeOfRightOperand", 0);
        else if (typeOfRightOperand == Reference.compareOperandType.CONSTSTR)
            packedCondition.set("typeOfRightOperand", 1);
        else
            packedCondition.set("typeOfRightOperand", 2);

        packedCondition.set("leftConversion", leftConversion);
        packedCondition.set("rightConversion", rightConversion);

        return packedCondition;
    }

    private Binder parseCondition(String condition) {

        int leftConversion = NO_CONVERSION;
        int rightConversion = NO_CONVERSION;

        for (int i = 0; i < 2; i++) {
            int operPos = condition.lastIndexOf(operators[i]);

            if ((operPos >= 0) && (condition.length() - operators[i].length() == operPos)) {

                String leftOperand = condition.substring(0, operPos).replaceAll("\\s+", "");

                if (leftOperand.endsWith("::number")) {
                    leftConversion = CONVERSION_BIG_DECIMAL;
                    leftOperand = leftOperand.substring(0, leftOperand.length() - 8);
                }

                return packCondition(i, leftOperand, null, compareOperandType.FIELD, compareOperandType.CONSTOTHER, leftConversion, rightConversion);
            }
        }

        for (int i = 2; i < INHERITS; i++) {
            int operPos = condition.indexOf(operators[i]);
            int firstMarkPos = condition.indexOf("\"");
            int lastMarkPos = condition.lastIndexOf("\"");

            // Normal situation - operator without quotes
            while ((operPos >= 0) && ((firstMarkPos >= 0) && (operPos > firstMarkPos) && (operPos < lastMarkPos)))
                operPos = condition.indexOf(operators[i], operPos + 1);

            // Operator not found
            if (operPos < 0)
                continue;

            // Parsing left operand
            String subStrL = condition.substring(0, operPos);
            if (subStrL.length() == 0)
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Missing left operand.");

            int lmarkPos1 = subStrL.indexOf("\"");
            int lmarkPos2 = subStrL.lastIndexOf("\"");

            if ((lmarkPos1 >= 0) && (lmarkPos1 == lmarkPos2))
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Only one quote is found for left operand.");

            String leftOperand;
            compareOperandType typeLeftOperand = compareOperandType.CONSTOTHER;

            if ((lmarkPos1 >= 0) && (lmarkPos1 != lmarkPos2)) {
                leftOperand = subStrL.substring(lmarkPos1 + 1, lmarkPos2);
                typeLeftOperand = compareOperandType.CONSTSTR;
            }
            else {
                leftOperand = subStrL.replaceAll("\\s+", "");
                int firstPointPos;
                if (((firstPointPos = leftOperand.indexOf(".")) > 0) &&
                        (leftOperand.length() > firstPointPos + 1) &&
                        ((leftOperand.charAt(firstPointPos + 1) < '0') ||
                                (leftOperand.charAt(firstPointPos + 1) > '9')))
                    typeLeftOperand = compareOperandType.FIELD;
            }

            // Parsing right operand
            String subStrR = condition.substring(operPos + operators[i].length());
            if (subStrR.length() == 0)
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Missing right operand.");

            int rmarkPos1 = subStrR.indexOf("\"");
            int rmarkPos2 = subStrR.lastIndexOf("\"");

            if ((rmarkPos1 >= 0) && (rmarkPos1 == rmarkPos2))
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Only one quote is found for rigth operand.");

            String rightOperand;
            compareOperandType typeRightOperand = compareOperandType.CONSTOTHER;

            if ((rmarkPos1 >= 0) && (rmarkPos1 != rmarkPos2)) {
                rightOperand = subStrR.substring(rmarkPos1 + 1, rmarkPos2);
                typeRightOperand = compareOperandType.CONSTSTR;
            }
            else {
                rightOperand = subStrR.replaceAll("\\s+", "");
                int firstPointPos;
                if (((firstPointPos = rightOperand.indexOf(".")) > 0) &&
                        (rightOperand.length() > firstPointPos + 1) &&
                        ((rightOperand.charAt(firstPointPos + 1) < '0') ||
                                (rightOperand.charAt(firstPointPos + 1) > '9')))
                    typeRightOperand = compareOperandType.FIELD;
            }

            //if ((typeLeftOperand != compareOperandType.FIELD) && (typeRightOperand != compareOperandType.FIELD))
            //    throw new IllegalArgumentException("At least one operand must be a field in condition: " + condition);

            if ((typeLeftOperand == compareOperandType.FIELD) && (leftOperand.endsWith("::number"))) {
                leftConversion = CONVERSION_BIG_DECIMAL;
                leftOperand = leftOperand.substring(0, leftOperand.length() - 8);
            }

            if ((typeRightOperand == compareOperandType.FIELD) && (rightOperand.endsWith("::number"))) {
                rightConversion = CONVERSION_BIG_DECIMAL;
                rightOperand = rightOperand.substring(0, rightOperand.length() - 8);
            }

            return packCondition(i, leftOperand, rightOperand, typeLeftOperand, typeRightOperand, leftConversion, rightConversion);
        }

        for (int i = INHERITS; i <= INHERIT; i++) {
            int operPos = condition.indexOf(operators[i]);

            if ((operPos == 0) || ((operPos > 0) && (condition.charAt(operPos - 1) != '_'))) {
                String subStrR = condition.substring(operPos + operators[i].length());
                if (subStrR.length() == 0)
                    throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Missing right operand.");

                String rightOperand = subStrR.replaceAll("\\s+", "");

                if (rightOperand.endsWith("::number")) {
                    rightConversion = CONVERSION_BIG_DECIMAL;
                    rightOperand = rightOperand.substring(0, rightOperand.length() - 8);
                }

                return packCondition(i, null, rightOperand, compareOperandType.FIELD, compareOperandType.FIELD, leftConversion, rightConversion);
            }
        }

        int operPos = condition.indexOf(operators[CAN_PLAY]);
        if (operPos > 0) {
            // Parsing left operand
            String subStrL = condition.substring(0, operPos);
            if (subStrL.length() == 0)
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Missing left operand.");

            String leftOperand = subStrL.replaceAll("\\s+", "");
            if (leftOperand.contains("."))
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Left operand must be a reference to a contract.");

            String subStrR = condition.substring(operPos + operators[CAN_PLAY].length());
            if (subStrR.length() == 0)
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Missing right operand.");

            // Parsing right operand
            String rightOperand = subStrR.replaceAll("\\s+", "");
            int firstPointPos;
            if (!(((firstPointPos = rightOperand.indexOf(".")) > 0) &&
                  (rightOperand.length() > firstPointPos + 1) &&
                  ((rightOperand.charAt(firstPointPos + 1) < '0') ||
                   (rightOperand.charAt(firstPointPos + 1) > '9'))))
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Right operand must be a role field.");

            return packCondition(CAN_PLAY, leftOperand, rightOperand, compareOperandType.CONSTOTHER, compareOperandType.FIELD, leftConversion, rightConversion);
        }

        throw new IllegalArgumentException("Invalid format of condition: " + condition);
    }

    /**
     * Check condition of reference
     * @param condition condition to check for matching
     * @param ref contract to check for matching
     * @param contracts contract list to check for matching
     * @param iteration check inside references iteration number
     * @return true if match or false
     */
    private boolean checkCondition(Binder condition, Contract ref, Collection<Contract> contracts, int iteration) {

        Reference.compareOperandType typeOfLeftOperand;
        Reference.compareOperandType typeOfRightOperand;

        String leftOperand = condition.getString("leftOperand", null);
        String rightOperand = condition.getString("rightOperand", null);
        int operator = condition.getIntOrThrow("operator");

        int typeLeftOperand = condition.getIntOrThrow("typeOfLeftOperand");
        int typeRightOperand = condition.getIntOrThrow("typeOfRightOperand");

        if (typeLeftOperand == 0)
            typeOfLeftOperand = Reference.compareOperandType.FIELD;
        else if (typeLeftOperand == 1)
            typeOfLeftOperand = Reference.compareOperandType.CONSTSTR;
        else
            typeOfLeftOperand = Reference.compareOperandType.CONSTOTHER;

        if (typeRightOperand == 0)
            typeOfRightOperand = Reference.compareOperandType.FIELD;
        else if (typeRightOperand == 1)
            typeOfRightOperand = Reference.compareOperandType.CONSTSTR;
        else
            typeOfRightOperand = Reference.compareOperandType.CONSTOTHER;

        int leftConversion = condition.getInt("leftConversion", NO_CONVERSION);
        int rightConversion = condition.getInt("rightConversion", NO_CONVERSION);

        boolean isBigDecimalConversion = (leftConversion == CONVERSION_BIG_DECIMAL) || (rightConversion == CONVERSION_BIG_DECIMAL);

        return compareOperands(ref, leftOperand, rightOperand, typeOfLeftOperand, typeOfRightOperand, isBigDecimalConversion, operator, contracts, iteration);
    }

    /**
     * Check not pre-parsed condition of reference (old version)
     * @param condition condition to check for matching
     * @param ref contract to check for matching
     * @param contracts contract list to check for matching
     * @param iteration check inside references iteration number
     * @return true if match or false
     */
    private boolean checkCondition(String condition, Contract ref, Collection<Contract> contracts, int iteration) {

        Binder parsed = parseCondition(condition);
        return checkCondition(parsed, ref, contracts, iteration);
    }

    /**
     * Pre-parsing conditions of reference
     * @param conds is binder of not-parsed (string) conditions
     * @return result {@link Binder} with parsed conditions
     */
    private Binder parseConditions(Binder conds) {

        if ((conds == null) || (conds.size() == 0))
            return null;

        if (conds.containsKey("operator"))
            return conds;

        boolean all = conds.containsKey(all_of.name());
        boolean any = conds.containsKey(any_of.name());

        if (all || any) {
            Binder result = new Binder();
            String keyName = all ? all_of.name() : any_of.name();
            List<Object> parsedList = new ArrayList<>();
            List<Object> condList = conds.getList(keyName, null);
            if (condList == null)
                throw new IllegalArgumentException("Expected all_of or any_of conditions");

            for (Object item: condList) {
                if (item.getClass().getName().endsWith("String"))
                    parsedList.add(parseCondition((String) item));
                else if (item.getClass().getName().endsWith("LinkedHashMap")) {
                    LinkedHashMap<String, Binder> insideHashMap = (LinkedHashMap<String, Binder>) item;
                    Binder insideBinder = new Binder(insideHashMap);
                    Binder parsed = parseConditions(insideBinder);
                    if (parsed != null)
                        parsedList.add(parsed);
                } else {
                    Binder parsed = parseConditions((Binder) item);
                    if (parsed != null)
                        parsedList.add(parsed);
                }
            }

            result.put(keyName, parsedList);
            return result;
        }
        else
            throw new IllegalArgumentException("Expected all_of or any_of");
    }

    /**
     * Check conditions of references
     * @param conditions binder with conditions to check for matching
     * @param ref contract to check for matching
     * @param contracts contract list to check for matching
     * @param iteration check inside references iteration number
     * @return true if match or false
     */
    private boolean checkConditions(Binder conditions, Contract ref, Collection<Contract> contracts, int iteration) {

        boolean result;

        if ((conditions == null) || (conditions.size() == 0))
            return true;

        if (conditions.containsKey(all_of.name()))
        {
            List<Object> condList = conditions.getList(all_of.name(), null);
            if (condList == null)
                throw new IllegalArgumentException("Expected all_of conditions");

            result = true;
            for (Object item: condList) {
                if (item.getClass().getName().endsWith("String"))
                    result = result && checkCondition((String) item, ref, contracts, iteration);        // not pre-parsed (old) version
                else
                    //LinkedHashMap<String, Binder> insideHashMap = (LinkedHashMap<String, Binder>) item;
                    //Binder insideBinder = new Binder(insideHashMap);
                    result = result && checkConditions((Binder) item, ref, contracts, iteration);
            }
        }
        else if (conditions.containsKey(any_of.name()))
        {
            List<Object> condList = conditions.getList(any_of.name(), null);
            if (condList == null)
                throw new IllegalArgumentException("Expected any_of conditions");

            result = false;
            for (Object item: condList) {
                if (item.getClass().getName().endsWith("String"))
                    result = result || checkCondition((String) item, ref, contracts, iteration);        // not pre-parsed (old) version
                else
                    //LinkedHashMap<String, Binder> insideHashMap = (LinkedHashMap<String, Binder>) item;
                    //Binder insideBinder = new Binder(insideHashMap);
                    result = result || checkConditions((Binder) item, ref, contracts, iteration);
            }
        }
        else if (conditions.containsKey("operator"))                                                    // pre-parsed version
            result = checkCondition(conditions, ref, contracts, iteration);
        else
            throw new IllegalArgumentException("Expected all_of or any_of");

        return result;
    }

    /**
     * Check if reference is valid i.e. have matching with criteria items
     * @return true or false
     */
    public boolean isValid() {
        return matchingItems.size() > 0;
    }

    /**
     * Check if given item matching with current reference criteria
     * @param a item to check for matching
     * @param contracts contract list to check for matching
     * @return true if match or false
     */
    public boolean isMatchingWith(Approvable a, Collection<Contract> contracts) {
        return isMatchingWith(a, contracts, 0);
    }

    /**
     * Check if given item matching with current reference criteria
     * @param a item to check for matching
     * @param contracts contract list to check for matching
     * @param iteration check inside references iteration number
     * @return true if match or false
     */
    public boolean isMatchingWith(Approvable a, Collection<Contract> contracts, int iteration) {
        //todo: add this checking for matching with given item

        if (iteration > 16)
            throw new IllegalArgumentException("Recursive checking references have more 16 iterations");

        boolean result = true;

        if(a instanceof Contract) {
            //check roles
            Contract contract = (Contract) a;
            if (result) {
                Map<String, Role> contractRoles = contract.getRoles();
                result = roles.isEmpty() || roles.stream().anyMatch(role -> contractRoles.containsKey(role));
            }

            //check origin
            if (result) {
                result = (origin == null || !(contract.getOrigin().equals(origin)));
            }

            //check fields
            if (result) {
                Binder stateData = contract.getStateData();
                result = fields.isEmpty() || fields.stream().anyMatch(field -> stateData.get(field) != null);
            }

            //check conditions
            if (result) {
                result = checkConditions(conditions, contract, contracts, iteration);
            }
        }

        return result;
    }

    private boolean isInherited(Reference ref, Contract refContract, Collection<Contract> contracts, int iteration) {
        return isInherited(conditions, ref, refContract, contracts, iteration);
    }

    private boolean isInherited(Binder conditions, Reference ref, Contract refContract, Collection<Contract> contracts, int iteration) {
        if ((conditions == null) || (conditions.size() == 0))
            return false;

        List<Object> condList = null;

        if (conditions.containsKey(all_of.name()))
        {
            condList = conditions.getList(all_of.name(), null);
            if (condList == null)
                throw new IllegalArgumentException("Expected all_of conditions");
        }
        else if (conditions.containsKey(any_of.name()))
        {
            condList = conditions.getList(any_of.name(), null);
            if (condList == null)
                throw new IllegalArgumentException("Expected any_of conditions");
        }
        else if (conditions.containsKey("operator"))
            return isInheritedParsed(conditions, ref, refContract, contracts, iteration);
        else
            throw new IllegalArgumentException("Expected all_of or any_of");

        if (condList != null)
            for (Object item: condList)
                if (item.getClass().getName().endsWith("String")) {
                    if (isInherited((String) item, ref, refContract, contracts, iteration))
                        return true;
                } else if (isInherited((Binder) item, ref, refContract, contracts, iteration))
                        return true;

        return false;
    }

    private boolean isInheritedParsed(Binder condition, Reference ref, Contract refContract, Collection<Contract> contracts, int iteration) {

        int operator = condition.getIntOrThrow("operator");
        String rightOperand = condition.getString("rightOperand", null);

        if (((operator == INHERITS) || (operator == INHERIT)) && (rightOperand != null))
            return isInheritedOperand(rightOperand, ref, refContract, contracts, iteration);

        return false;
    }

    private boolean isInherited(String condition, Reference ref, Contract refContract, Collection<Contract> contracts, int iteration) {
        for (int i = INHERITS; i <= INHERIT; i++) {
            int operPos = condition.indexOf(operators[i]);

            if ((operPos == 0) || ((operPos > 0) && (condition.charAt(operPos - 1) != '_'))) {
                String subStrR = condition.substring(operPos + operators[i].length());
                if (subStrR.length() == 0)
                    throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Missing right operand.");

                String rightOperand = subStrR.replaceAll("\\s+", "");

                return isInheritedOperand(rightOperand, ref, refContract, contracts, iteration);
            }
        }

        return false;
    }

    private boolean isInheritedOperand(String rightOperand, Reference ref, Contract refContract, Collection<Contract> contracts, int iteration) {
        Contract rightOperandContract = null;
        Object right = null;
        int firstPointPos;

        if (rightOperand.startsWith("ref.")) {
            rightOperand = rightOperand.substring(4);
            rightOperandContract = refContract;
        } else if (rightOperand.startsWith("this.")) {
            if (baseContract == null)
                throw new IllegalArgumentException("Use right operand in condition: " + rightOperand + ". But this contract not initialized.");

            rightOperand = rightOperand.substring(5);
            rightOperandContract = baseContract;
        } else if ((firstPointPos = rightOperand.indexOf(".")) > 0) {
            if (baseContract == null)
                throw new IllegalArgumentException("Use right operand in condition: " + rightOperand + ". But this contract not initialized.");

            Reference refLink = baseContract.findReferenceByName(rightOperand.substring(0, firstPointPos));
            if (refLink == null)
                throw new IllegalArgumentException("Not found reference: " + rightOperand.substring(0, firstPointPos));

            for (Contract checkedContract : contracts)
                if (refLink.isMatchingWith(checkedContract, contracts, iteration + 1))
                    rightOperandContract = checkedContract;

            if (rightOperandContract == null)
                return false;

            rightOperand = rightOperand.substring(firstPointPos + 1);
        } else
            throw new IllegalArgumentException("Invalid format of right operand in condition: " + rightOperand + ". Missing contract field.");

        if (rightOperandContract != null)
            right = rightOperandContract.get(rightOperand);

        if ((right == null) || !right.getClass().getName().endsWith("Reference"))
            throw new IllegalArgumentException("Expected reference in condition in right operand: " + rightOperand);

        if (((Reference) right).equals(ref))
            return true;

        return false;
    }

    /**
     * Get the name from the reference
     * @return name reference
     */
    public String getName() {
        return name;
    }

    /**
     * Set the name for the reference
     * @return this reference
     */
    public Reference setName(String name) {
        if (name != null)
            this.name = name;
        else
            this.name = "";
        return this;
    }

    /**
     * Get comment of the reference
     *
     * @return comment of the reference (may be null)
     */
    public String getComment() {
        return comment;
    }

    /**
     * Set comment of the reference
     *
     * @param comment of the reference
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * Get the list of roles from the reference
     * @return roles from reference
     */
    public List<String> getRoles() {
        return roles;
    }

    /**
     * Add the roles for the reference
     * @return this reference
     */
    public Reference addRole(String role) {
        this.roles.add(role);
        return this;
    }

    /**
     * Set the list of roles for the reference
     * @return this reference
     */
    public Reference setRoles(List<String> roles) {
        this.roles = roles;
        return this;
    }

    /**
     * Get the list of fields from the reference
     * @return list of fields reference
     */
    public List<String> getFields() {
        return fields;
    }

    /**
     * Add the field for the reference
     * @return this reference
     */
    public Reference addField(String field) {
        this.fields.add(field);
        return this;
    }

    /**
     * Set the list of fields for the reference
     * @return this reference
     */
    public Reference setFields(List<String> fields) {
        this.fields = fields;
        return this;
    }

    /**
     * Assembly condition of reference
     * @param condition is binder of parsed condition
     * @return result {@link String} with assembled condition
     */
    private String assemblyCondition(Binder condition) {

        if ((condition == null) || (condition.size() == 0))
            return null;

        String result = "";

        // get parsed data
        String leftOperand = condition.getString("leftOperand", null);
        String rightOperand = condition.getString("rightOperand", null);
        int operator = condition.getIntOrThrow("operator");

        int leftConversion = condition.getInt("leftConversion", NO_CONVERSION);
        int rightConversion = condition.getInt("rightConversion", NO_CONVERSION);

        int typeLeftOperand = condition.getIntOrThrow("typeOfLeftOperand");
        int typeRightOperand = condition.getIntOrThrow("typeOfRightOperand");

        // assembly condition
        if (leftOperand != null) {
            if (typeLeftOperand == 1)      // CONSTSTR
                result += "\"";

            result += leftOperand;

            if (typeLeftOperand == 1)      // CONSTSTR
                result += "\"";

            if (leftConversion == CONVERSION_BIG_DECIMAL)
                result += "::number";
        }

        result += operators[operator];

        if (rightOperand != null) {
            if (typeRightOperand == 1)      // CONSTSTR
                result += "\"";

            result += rightOperand;

            if (typeRightOperand == 1)      // CONSTSTR
                result += "\"";

            if (rightConversion == CONVERSION_BIG_DECIMAL)
                result += "::number";
        }

        return result;
    }

    /**
     * Assembly conditions of reference
     * @param conds is binder of parsed conditions
     * @return result {@link Binder} with assembled (string) conditions
     */
    private Binder assemblyConditions(Binder conds) {

        if ((conds == null) || (conds.size() == 0))
            return null;

        boolean all = conds.containsKey(all_of.name());
        boolean any = conds.containsKey(any_of.name());

        if (all || any) {
            Binder result = new Binder();
            String keyName = all ? all_of.name() : any_of.name();
            List<Object> assembledList = new ArrayList<>();
            List<Object> condList = conds.getList(keyName, null);
            if (condList == null)
                throw new IllegalArgumentException("Expected all_of or any_of conditions");

            for (Object item: condList) {
                if (item.getClass().getName().endsWith("String"))       // already assembled condition
                    assembledList.add(item);
                else {
                    Binder parsed = null;
                    String cond = null;
                    if (item.getClass().getName().endsWith("LinkedHashMap")) {
                        LinkedHashMap<String, Binder> insideHashMap = (LinkedHashMap<String, Binder>) item;
                        Binder insideBinder = new Binder(insideHashMap);
                        parsed = assemblyConditions(insideBinder);
                    } else if (((Binder) item).containsKey("operator"))
                        cond = assemblyCondition((Binder) item);
                    else
                        parsed = assemblyConditions((Binder) item);

                    if (parsed != null)
                        assembledList.add(parsed);

                    if (cond != null)
                        assembledList.add(cond);
                }
            }

            result.put(keyName, assembledList);
            return result;
        }
        else
            throw new IllegalArgumentException("Expected all_of or any_of");
    }

    /**
     * Get the conditions from the reference
     * @return conditions reference
     */
    public Binder getConditions() {
        return conditions;
    }

    /**
     * Export the conditions from the reference as strings
     * @return strings conditions reference
     */
    public Binder exportConditions() {
        return assemblyConditions(conditions);
    }

    /**
     * Set the conditions from the reference
     * @return this reference
     */
    public Reference setConditions(Binder conditions) {
        this.conditions = parseConditions(conditions);
        return this;
    }

    //TODO: The method allows to mark the contract as matching reference, bypassing the validation
    /**
     * Add the matching item for the reference
     * @return this reference
     */
    public Reference addMatchingItem(Approvable a) {
        this.matchingItems.add(a);
        return this;
    }

    /**
     * Get the base contract in which the reference is located
     * @return base contract
     */
    public Contract getContract() {
        return baseContract;
    }

    /**
     * Set the base contract from the reference
     * @return this reference
     */
    public Reference setContract(Contract contract) {
        baseContract = contract;
        return this;
    }

    @Override
    public String toString() {
        String res = "{";
        res += "name:"+name;
        res += ", type:"+type;
        if (comment != null)
            res += ", comment:"+comment;
        if (transactional_id.length() > 8)
            res += ", transactional_id:"+transactional_id.substring(0, 8)+"...";
        else
            res += ", transactional_id:"+transactional_id;
        res += ", contract_id:"+contract_id;
        res += ", required:"+required;
        res += ", origin:"+origin;
        res += ", signed_by:[";
        for (int i = 0; i < signed_by.size(); ++i) {
            if (i > 0)
                res += ", ";
            Role r = signed_by.get(i);
            res += r.toString();
        }
        res += "]";
        res += "}";
        return res;
    }

    static {
        Config.forceInit(Reference.class);
        DefaultBiMapper.registerClass(Reference.class);
    }
};
