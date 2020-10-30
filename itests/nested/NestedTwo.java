package nested;

import othernested.OtherThree;

public class NestedTwo {

    public static void main(String... args) {
        System.out.println(NestedOne.class.getName());

        System.out.println(OtherThree.class.getName());
    }
}