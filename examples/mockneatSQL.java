///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS net.andreinc.mockneat:mockneat:0.3.9

import static net.andreinc.mockneat.unit.companies.Departments.departments;
import static net.andreinc.mockneat.unit.objects.From.fromInts;
import static net.andreinc.mockneat.unit.seq.IntSeq.intSeq;
import static net.andreinc.mockneat.unit.seq.Seq.seq;
import static net.andreinc.mockneat.unit.text.SQLInserts.sqlInserts;
import static net.andreinc.mockneat.unit.user.Names.names;

import java.util.List;

import net.andreinc.mockneat.unit.text.sql.SQLTable;
import net.andreinc.mockneat.unit.text.sql.escapers.MySQL;

public class mockneatSQL {

    public static void main(String... args) {
        final int numEmployees = 1000;
        final int numManagers = 10;
        final int numDeps = 5;
        
        // The employees ids: [0, 10, 20, ....]
        List<Integer> employeesIds = intSeq().increment(10).list(numEmployees).get();
        // Random values from the employees ids list
        List<Integer> managerIds = fromInts(employeesIds).list(numManagers).get();
        
        SQLTable departments = sqlInserts()
                                .tableName("deps")
                                  .column("id", intSeq())
                                  .column("name", departments(), MySQL.TEXT_BACKSLASH)
                                .table(numDeps) // Groups the SQL Inserts into a table
                                .get(); // Retrieves the "table" representation
        
        SQLTable employees = sqlInserts()
                                .tableName("emps")
                                  .column("id", seq(employeesIds))
                                  .column("first_name", names().first(), MySQL.TEXT_BACKSLASH)
                                  .column("last_name", names().last(), MySQL.TEXT_BACKSLASH)
                                  .column("email", "NULL", MySQL.TEXT_BACKSLASH)
                                  .column("manager_id", fromInts(managerIds))
                                  .column("dep_id", departments.fromColumn("id"))
                                .table(numEmployees) // Groups the SQL Inserts inside a table
                                .get()
                                .updateAll((rowNum, row) -> {
                                    // Updates each insert so that the email field
                                    // is formatted as "(first_name)_(last_name)@company.com"
                                    String firstName = row.getValue("first_name").toLowerCase();
                                    String lastName = row.getValue("last_name").toLowerCase();
                                    row.setValue("email", firstName + "_" + lastName + "@company.com");
                                });
        
        System.out.println(departments);
        System.out.println(employees);
    }
}
