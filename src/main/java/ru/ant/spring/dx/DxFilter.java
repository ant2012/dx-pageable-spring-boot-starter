package ru.ant.spring.dx;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonValue;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;

import javax.persistence.criteria.*;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.lang.String.format;

@SuppressWarnings("unused")
public class DxFilter<E> implements Specification<E> {
    private enum GroupOperator {and, or}
    private final Specification<E> spec;
    private final ConversionService conversionService;

    public DxFilter(String filter) {
        spec = filter == null || filter.isEmpty()
                ? null
                : Specification.where(buildSpec(Json.createReader(new StringReader(filter)).readArray()));
        conversionService = DxContextKeeper.getCtx().getBean(ConversionService.class);
    }

    @Override
    public Predicate toPredicate(@NonNull Root<E> root, @NonNull CriteriaQuery<?> query, @NonNull CriteriaBuilder cb) {
        return spec == null ? null : spec.toPredicate(root, query, cb);
    }

    private Specification<E> buildSpec(JsonArray jsonArray) {
        switch (jsonArray.size()){
            case 0:
            case 1:
                throw new NotImplementedException(format("JsonArray size of [%1$s] not supported", jsonArray.size()));
            case 2:
                String notProbe = jsonArray.getString(0);
                if(!notProbe.equals("!"))
                    throw new NotImplementedException("Filter array of size [2] have to be unary NOT operation");
                return Specification.not(buildSpec(jsonArray.getJsonArray(1)));
            case 3:
                return switch (jsonArray.get(0).getValueType()) {
                    case STRING -> new BinaryCriteriaSpecification<>(jsonArray);
                    case ARRAY -> buildGroupFilterOperation(jsonArray);
                    default -> throw new NotImplementedException(format("The type [%1$s] of the first element of filter array not supported", jsonArray.get(0).getValueType()));
                };
            default:
                if((jsonArray.size() & 1) == 0)
                    throw new NotImplementedException(format("JsonArray size of [%1$s] not supported", jsonArray.size()));
                return buildGroupFilterOperation(jsonArray);
        }
    }

    private Specification<E> buildGroupFilterOperation(JsonArray jsonArray) {
        Specification<E> spec = buildSpec(jsonArray.getJsonArray(0));
        GroupOperator groupOperator = GroupOperator.valueOf(jsonArray.getString(1).toLowerCase());
        for (int i = 1; i < jsonArray.size(); i+=2) {
            GroupOperator go = GroupOperator.valueOf(jsonArray.getString(i).toLowerCase());
            if(!groupOperator.equals(go))
                throw new NotImplementedException(format("Can not combine different group operators (%1$s) on the same array level", (Object) GroupOperator.values()));
            switch (groupOperator) {
                case and -> spec = spec.and(buildSpec(jsonArray.getJsonArray(1 + i)));
                case or -> spec = spec.or(buildSpec(jsonArray.getJsonArray(1 + i)));
            }
        }
        return spec;
    }

    private class BinaryCriteriaSpecification<T> implements Specification<E> {
        private final JsonArray jsonArray;

        public BinaryCriteriaSpecification(JsonArray jsonArray) {
            this.jsonArray = jsonArray;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override
        public Predicate toPredicate(@NonNull Root<E> root, @NonNull CriteriaQuery<?> query, @NonNull CriteriaBuilder cb) {
            String propertyPath = jsonArray.getString(0);
            Path<T> property = createExpression(root, propertyPath);
            Class<? extends T> propertyType = property.getJavaType();

            String comparisonOperator = jsonArray.getString(1);
            Object value = getValue(propertyType, jsonArray);


            return switch (comparisonOperator) {
                case "=" -> value != null ? cb.equal(property, value) : cb.isNull(property);
                case "<>" -> value != null ? cb.notEqual(property, value) : cb.isNotNull(property);
                case ">" -> cb.greaterThan(root.get(propertyPath), (Comparable) value);
                case ">=" -> cb.greaterThanOrEqualTo(root.get(propertyPath), (Comparable) value);
                case "<" -> cb.lessThan(root.get(propertyPath), (Comparable) value);
                case "<=" -> cb.lessThanOrEqualTo(root.get(propertyPath), (Comparable) value);
                case "startswith" -> cb.like(root.get(propertyPath), value + "%");
                case "endswith" -> cb.like(root.get(propertyPath), "%" + value);
                case "contains" -> cb.like(root.get(propertyPath), "%" + value + "%");
                case "notcontains" -> cb.notLike(root.get(propertyPath), "%" + value + "%");
                case "anyof" -> property.in((Collection<?>) value);
                case "noneof" -> property.in((Collection<?>) value).not();
                default -> throw new NotImplementedException(String.format("Comparison operator [%1$s] not supported", comparisonOperator));
            };
        }
        private Path<T> createExpression(Root<E> root, String propertyName) {
            String[] pathParts = propertyName.split("\\.");
            Path<T> exp = null;
            for (String part : pathParts) {
                exp = exp == null ? root.get(part) : exp.get(part);
            }
            return exp;
        }

        private Object getValue(Class<? extends T> propertyType, JsonArray jsonArray) {
            String comparisonOperator = jsonArray.getString(1);
            return switch (comparisonOperator) {
                case "=", "<>", ">", ">=", "<", "<=", "startswith", "endswith", "contains", "notcontains" -> getSingleValue(propertyType, jsonArray, 2);
                case "anyof", "noneof" -> getValueCollection(propertyType, jsonArray);
                default -> throw new NotImplementedException(String.format("Comparison operator [%1$s] not supported", comparisonOperator));
            };

        }

        private Collection<?> getValueCollection(Class<? extends T> propertyType, JsonArray jsonArray) {
            JsonArray valuesJsonArray = jsonArray.getJsonArray(2);
            List<Object> valuesCollection = new ArrayList<>();
            for (int i = 0; i < valuesJsonArray.size(); i++) {
                valuesCollection.add(getSingleValue(propertyType, valuesJsonArray, i));
            }
            return valuesCollection;

        }

        private Object getSingleValue(Class<? extends T> propertyType, JsonArray jsonArray, int i) {
            JsonValue jsonValue = jsonArray.get(i);
            return switch (jsonValue.getValueType()) {
                case STRING -> getValueFromJsonString(propertyType, jsonArray.getString(i));
                case NULL -> null;
                case NUMBER -> jsonArray.getJsonNumber(i).numberValue();
                case TRUE, FALSE -> jsonArray.getBoolean(i);
                default ->
                        throw new IllegalArgumentException(format("Oik can not be of type [%1$s]", jsonValue.getValueType().name()));
            };
        }
        private Object getValueFromJsonString(Class<?> propertyType, String s) {
            return conversionService.convert(s, propertyType);
        }
    }
}