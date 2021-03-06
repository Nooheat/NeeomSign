package com.nooheat.model;

import com.nooheat.database.DBManager;
import com.nooheat.manager.UserManager;
import com.nooheat.support.Category;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Created by NooHeat on 17/09/2017.
 */
public class Survey extends Letter implements Statistic {
    private final static String SURVEY_SAVE = "INSERT INTO survey(letterNumber,writerUid, title, summary, openDate, closeDate) VALUES(?,?,?,?,?,?);";
    private final static String SURVEY_UPDATE = "UPDATE SURVEY SET title = ?, summary = ?, openDate = ?, closeDate = ? WHERE letterNumber = ?";
    private final static String QUESTION_SAVE = "INSERT INTO surveyQuestion(letterNumber,columnIndex,question) VALUES(?,?,?)";
    private final static String SURVEY_FINDALL = "SELECT s.letterNumber, s.title, s.writerUid, s.summary, s.openDate, s.closeDate, a.name AS writerName, (SELECT count(*)>=1 " +
            "FROM surveyAnswer as SA WHERE SA.uid = ? AND SA.letterNumber = s.letterNumber) as isAnswered  " +
            "FROM survey AS s LEFT JOIN ADMIN AS a ON s.writerUid = a.uid ORDER BY letterNumber;";
    private final static String SURVEY_FINDONE = "SELECT s.letterNumber, s.title, s.writerUid, s.summary, s.openDate, s.closeDate, a.name AS writerName " +
            "FROM survey AS s LEFT JOIN ADMIN AS a ON s.writerUid = a.uid " +
            "WHERE letterNumber = ?;";
    private final static String SURVEY_DELETE = "DELETE FROM survey WHERE letterNumber = ?";
    private final static String QUESTION_DELETE = "DELETE FROM surveyQuestion WHERE letterNumber = ?";
    private final static String ANSWER_SAVE = "INSERT INTO  surveyAnswer(uid, letterNumber, columnIndex, answer, answerDate) VALUES(?, ?, ?, ?, ?);";
    private final static String ANSWER_MODIFY = "UPDATE surveyAnswer SET answer = ?, answerDate = ? WHERE uid = ? AND letterNumber = ? AND columnIndex = ?;";
    private final static String COUNT_ANSWER = "SELECT count(distinct uid) as count FROM surveyAnswer WHERE letterNumber = ?;";
    private final static String ANSWER_COUNT_STUDENT = "SELECT COUNT(distinct uid) AS count " +
            "FROM surveyAnswer AS A " +
            "WHERE letterNumber = ? AND (SELECT identity FROM USER AS U WHERE A.uid = U.uid) = 'child' AND (SELECT stuNum FROM USER AS U WHERE A.uid = U.uid) like ?;";
    private final static String ANSWER_COUNT_PARENT = "SELECT COUNT(distinct uid) AS count " +
            "FROM surveyAnswer AS A " +
            "WHERE letterNumber = ? AND (SELECT identity FROM USER AS U WHERE A.uid = U.uid) = 'parent' AND (SELECT stuNum FROM USER AS U WHERE A.uid = U.uid) like ?;";

    // TODO: ANSWER_COUNT_PARENT_ALL, ANSWER_COUNT_STUDENT_ALL
    private final static String ANSWER_COUNT_PARENT_ALL = "SELECT COUNT(distinct uid) AS count " +
            "FROM surveyAnswer AS A " +
            "WHERE letterNumber = ? AND (SELECT identity FROM USER AS U WHERE A.uid = U.uid) = 'parent';";

    private final static String ANSWER_COUNT_STUDENT_ALL = "SELECT COUNT(distinct uid) AS count " +
            "FROM surveyAnswer AS A " +
            "WHERE letterNumber = ? AND (SELECT identity FROM USER AS U WHERE A.uid = U.uid) = 'child';";

    private int letterNumber;
    private String summary;
    private List items;
    private List answerForms;
    private String closeDate;
    private String writerName;

    public Survey() throws SQLException {
        this.letterNumber = getNextLetterNumber();
        this.type = Category.SURVEY;
    }

    public Survey(int letterNumber, String writerUid, String title, String summary, List items, List answerForms, String openDate, String closeDate) throws SQLException {
        this.type = Category.SURVEY;
        this.letterNumber = letterNumber;
        this.writerUid = writerUid;
        this.title = title;
        this.summary = summary;
        this.items = items;
        this.openDate = openDate;
        this.closeDate = closeDate;
        this.answerForms = answerForms;
    }

    public Survey(String writerUid, String title, String summary, List items, List answerForms, String openDate, String closeDate) throws SQLException {
        this(getNextLetterNumber(), writerUid, title, summary, items, answerForms, openDate, closeDate);
    }

    public boolean save() throws SQLException {
        boolean surveySave = DBManager.update(SURVEY_SAVE, letterNumber, writerUid, title, summary, openDate, closeDate) == 1;
        boolean questionSave = saveItems();
        boolean answerFormSave = saveAnswerForms();
        if (surveySave && questionSave && answerFormSave) DBManager.commit();
        else DBManager.rollback();
        return surveySave && questionSave && answerFormSave;
    }

    public boolean saveAnswerForms() throws SQLException {

        int columnIndex = 1;
        for (int i = 0; i < answerForms.size(); i++) {
            //for (Object answerForm : answerForms) {
            System.out.println("ASDASDA");
            int answerFormIndex = 1;
            System.out.println("DDASD" + answerForms.get(i).toString());
            List answerSheet = Arrays.asList(answerForms.get(i).toString().substring(1, answerForms.get(i).toString().length() - 1).split(", "));
            for (Object answer : answerSheet) {
                if (DBManager.update("INSERT INTO surveyAnswerSheet(letterNumber,questionNumber,answerNumber, answer) VALUES(?,?,?,?);", letterNumber, columnIndex, answerFormIndex++, answer) == -1) {
                    DBManager.rollback();
                    return false;
                }
            }
            columnIndex++;

        }
        DBManager.commit();
        return true;
    }

    public boolean saveItems() throws SQLException {
        int columnIndex = 1;

        for (Object question : items) {
            if (DBManager.update(QUESTION_SAVE, letterNumber, columnIndex++, question) == -1) {
                DBManager.rollback();
                return false;
            }
        }

        DBManager.commit();
        return true;
    }

    public boolean saveUpdated() throws SQLException {
        boolean surveySave = DBManager.update(SURVEY_UPDATE, title, summary, openDate, closeDate, letterNumber) != -1;
        boolean answerFormsDeleted = DBManager.update("DELETE FROM surveyAnswerSheet WHERE letterNumber =?", letterNumber) != -1;
        boolean answersDeleted = DBManager.update("DELETE FROM surveyAnswer WHERE letterNumber = ?", letterNumber) != -1;
        boolean isDeletedAll = DBManager.update(QUESTION_DELETE, this.letterNumber) != -1;
        boolean questionSave = saveItems();
        boolean answerFormsSaved = saveAnswerForms();

        if (surveySave && isDeletedAll && questionSave && answersDeleted && answerFormsDeleted && answerFormsSaved)
            DBManager.commit();
        else DBManager.rollback();

        return surveySave && isDeletedAll && questionSave && answersDeleted && answerFormsDeleted && answerFormsSaved;
    }

    public static JsonArray findAll(String uid) throws SQLException {
        ResultSet rs = DBManager.execute(SURVEY_FINDALL, uid);
        List<JsonObject> list = new ArrayList<>();
        JsonArray result;
        while (rs.next()) {
            System.out.println("!@#!@#");
            JsonObject object = new JsonObject();
            object.put("letterNumber", rs.getInt("letterNumber"));
            object.put("title", rs.getString("title"));
            object.put("summary", rs.getString("summary"));
            object.put("openDate", rs.getString("openDate"));
            object.put("closeDate", rs.getString("closeDate"));
            object.put("writerName", rs.getString("writerName"));
            object.put("isAnswered", rs.getBoolean("isAnswered"));
            object.put("type", "SURVEY");

            list.add(object);
        }

        list.sort((o1, o2) -> o2.getString("openDate").compareTo(o1.getString("openDate")) * -1);

        result = new JsonArray(list.toString());

        return result;
    }

    public static Survey findOne(int letterNumber) throws SQLException {
        ResultSet rs = DBManager.execute(SURVEY_FINDONE, letterNumber);
        if (rs.next()) {
            Survey survey = new Survey();
            survey.setLetterNumber(rs.getInt("letterNumber"));
            survey.setWriterUid(rs.getString("writerUid"));
            survey.setTitle(rs.getString("title"));
            survey.setSummary(rs.getString("summary"));
            survey.setOpenDate(rs.getString("openDate"));
            survey.setCloseDate(rs.getString("closeDate"));
            survey.setWriterName(rs.getString("writerName"));

            ResultSet inner_rs = DBManager.execute("SELECT columnIndex, question FROM surveyQuestion WHERE letterNumber = ?", rs.getInt("letterNumber"));

            List<String> items = new ArrayList<>();

            List<List<String>> answerForms = new ArrayList<>();

            int questionNumber = 1;
            while (inner_rs.next()) {

                items.add(inner_rs.getString("question"));

                ArrayList<String> answerSheet = new ArrayList<>();
                ResultSet answerSheetRs = DBManager.execute("SELECT answer FROM surveyAnswerSheet WHERE letterNumber = ? AND questionNumber = ? ORDER BY answerNumber", letterNumber, questionNumber++);
                while (answerSheetRs.next()) {
                    answerSheet.add(answerSheetRs.getString("answer"));
                }
                answerForms.add(answerSheet);
            }


            survey.setItems(items);
            survey.setAnswerForms(answerForms);

            return survey;
        } else return null;
    }

    public static List<Survey> getSurveyList(String uid) throws SQLException {
        ResultSet rs = DBManager.execute(SURVEY_FINDALL, uid);

        List<Survey> surveys = new ArrayList<>();

        while (rs.next()) {
            Survey survey = new Survey();
            survey.setLetterNumber(rs.getInt("letterNumber"));
            survey.setWriterUid(rs.getString("writerUid"));
            survey.setTitle(rs.getString("title"));
            survey.setSummary(rs.getString("summary"));
            survey.setOpenDate(rs.getString("openDate"));
            survey.setCloseDate(rs.getString("closeDate"));
            survey.setWriterName(rs.getString("writerName"));
            survey.setAnswered(rs.getBoolean("isAnswered"));
            int letterNumber = rs.getInt("letterNumber");
            ResultSet inner_rs = DBManager.execute("SELECT columnIndex, question FROM surveyQuestion WHERE letterNumber = ?", letterNumber);

            List<String> items = new ArrayList<>();

            List<List<String>> answerForms = new ArrayList<>();

            int questionNumber = 1;
            while (inner_rs.next()) {

                items.add(inner_rs.getString("question"));

                ArrayList<String> answerSheet = new ArrayList<>();
                ResultSet answerSheetRs = DBManager.execute("SELECT answer FROM surveyAnswerSheet WHERE letterNumber = ? AND questionNumber = ? ORDER BY answerNumber", letterNumber, questionNumber);
                while (answerSheetRs.next()) {
                    answerSheet.add(answerSheetRs.getString("answer"));
                }
                answerForms.add(answerSheet);
            }


            survey.setItems(items);
            survey.setAnswerForms(answerForms);
            surveys.add(survey);
        }

        return surveys;
    }

    @Override
    public String toString() {
        return new JsonObject()
                .put("writerName", this.writerName)
                .put("type", this.type)
                .put("letterNumber", this.letterNumber)
                .put("writerUid", this.writerUid)
                .put("title", this.title)
                .put("summary", this.summary)
                .put("items", this.items)
                .put("openDate", this.openDate)
                .put("closeDate", this.closeDate)
                .put("isAnswered", this.isAnswered)
                .put("answerForms", this.answerForms)
                .toString();
    }

    @Override
    public JsonObject toJson() {
        return new JsonObject()
                .put("writerName", this.writerName)
                .put("type", this.type)
                .put("letterNumber", this.letterNumber)
                .put("writerUid", this.writerUid)
                .put("title", this.title)
                .put("summary", this.summary)
                .put("items", this.items)
                .put("openDate", this.openDate)
                .put("isAnswered", this.isAnswered)
                .put("closeDate", this.closeDate)
                .put("answerForms", this.answerForms);
    }

    public Survey update(String title, String summary, List items, List answerForms, String openDate, String closeDate) {
        this.title = title;
        this.summary = summary;
        this.items = items;
        this.openDate = openDate;
        this.closeDate = closeDate;
        this.answerForms = answerForms;
        return this;
    }

    public String getWriterUid() {
        return this.writerUid;
    }

    public boolean delete() throws SQLException {
        boolean answerFormsDeleted = DBManager.update("DELETE FROM surveyAnswerForms WHERE letterNumber = ?", letterNumber) != -1;
        boolean areQuestionsDeleted = DBManager.update(QUESTION_DELETE, this.letterNumber) != -1;
        boolean isSurveyDeleted = DBManager.update(SURVEY_DELETE, this.letterNumber) != -1;
        boolean answersDeleted = DBManager.update("DELETE FROM surveyAnswer WHERE letterNumber = ?", letterNumber) != -1;
        if (answersDeleted && isSurveyDeleted && answersDeleted && answerFormsDeleted)
            DBManager.commit();
        else DBManager.rollback();

        return areQuestionsDeleted && isSurveyDeleted && answersDeleted;
    }

    public boolean answer(String uid, List answers, String answerDate) throws SQLException {
        Iterator<Integer> iterator = answers.iterator();
        int columnIndex = 1;
        while (iterator.hasNext()) {
            int answer = iterator.next();

            if (DBManager.update(ANSWER_SAVE, uid, this.letterNumber, columnIndex++, answer, answerDate) == -1) {
                DBManager.rollback();
                return false;
            }
        }
        DBManager.commit();
        return true;
    }

    public int getCountOfAnswers() throws SQLException {
        ResultSet rs = DBManager.execute(COUNT_ANSWER, this.letterNumber);

        if (rs.next()) {
            return rs.getInt("count");
        } else return -1;
    }

    public void setLetterNumber(int letterNumber) {
        this.letterNumber = letterNumber;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setWriterUid(String writerUid) {
        this.writerUid = writerUid;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setItems(List items) {
        this.items = items;
    }

    public void setCloseDate(String closeDate) {
        this.closeDate = closeDate;
    }

    public void setOpenDate(String openDate) {
        this.openDate = openDate;
    }

    public String getWriterName() {
        return writerName;
    }

    public void setWriterName(String writerName) {
        this.writerName = writerName;
    }

    public int getLetterNumber() {
        return letterNumber;
    }

    public String getTitle() {
        return title;
    }

    public List getItems() {
        return items;
    }

    public String getCloseDate() {
        return closeDate;
    }

    public String getOpenDate() {
        return openDate;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isAnswered() {
        return isAnswered;
    }

    public void setAnswered(boolean answered) {
        isAnswered = answered;
    }

    @Override
    public XSSFWorkbook getStatistic() throws SQLException {
        XSSFWorkbook statistic = new XSSFWorkbook();
        XSSFSheet sheet = statistic.createSheet("통계");
        sheet = createFirstSheet(sheet);

        sheet = statistic.createSheet("세부통계");

        sheet = createSecondSheet(sheet);
        return statistic;
    }

    private XSSFSheet createFirstSheet(XSSFSheet sheet) throws SQLException {
        DecimalFormat form = new DecimalFormat("#.##");

        int rowCount = 0;
        XSSFRow row = sheet.createRow(rowCount++);

        row.createCell(0).setCellValue("제목");
        row.createCell(1).setCellValue(title);
        row = sheet.createRow(rowCount++);
        row.createCell(0).setCellValue("작성자");
        row.createCell(1).setCellValue(writerName);
        rowCount++;
        row = sheet.createRow(rowCount++);
        row.createCell(0).setCellValue("응답 현황");
        row = sheet.createRow(rowCount++);
        row.createCell(0).setCellValue("학생");
        row.createCell(1).setCellValue("1반");
        row.createCell(2).setCellValue("2반");
        row.createCell(3).setCellValue("3반");
        row.createCell(4).setCellValue("4반");
        row.createCell(5).setCellValue("총합");
        for (int i = 1; i <= 3; i++) {
            row = sheet.createRow(rowCount++);
            row.createCell(0).setCellValue(i + "학년");
            for (int j = 1; j <= 4; j++) {
                if (UserManager.getChildCount(i, j) == 0) row.createCell(j).setCellValue("학생이 없음");
                else
                    row.createCell(j).setCellValue(getStudentAnswerCount(i, j) + " (" + form.format(getStudentAnswerCount(i, j) / (double) UserManager.getChildCount(i, j) * 100) + "%)");
            }
            if (UserManager.getChildCount(i) == 0) row.createCell(5).setCellValue("학생이 없음");
            else
                row.createCell(5).setCellValue(getStudentAnswerCount(i) + " (" + form.format(getStudentAnswerCount(i) / (double) UserManager.getChildCount(i) * 100) + "%)");
        }

        row = sheet.createRow(rowCount++);
        row.createCell(0).setCellValue("총합");
        row.createCell(5).setCellValue(getStudentAnswerCount() + " (" + form.format(getStudentAnswerCount() / (double) UserManager.getChildCount() * 100) + "%)");

        rowCount++;
        row = sheet.createRow(rowCount++);
        row.createCell(0).setCellValue("학부모");
        row.createCell(1).setCellValue("1반");
        row.createCell(2).setCellValue("2반");
        row.createCell(3).setCellValue("3반");
        row.createCell(4).setCellValue("4반");
        row.createCell(5).setCellValue("총합");
        for (int i = 1; i <= 3; i++) {
            row = sheet.createRow(rowCount++);
            row.createCell(0).setCellValue(i + "학년");
            for (int j = 1; j <= 4; j++) {
                if (UserManager.getParentCount(i, j) == 0) row.createCell(j).setCellValue("학부모 없음");
                else
                    row.createCell(j).setCellValue(getParentAnswerCount(i, j) + " (" + form.format(getParentAnswerCount(i, j) / (double) UserManager.getParentCount(i, j) * 100) + "%)");
            }
            if (UserManager.getParentCount(i) == 0) row.createCell(5).setCellValue("학부모 없음");
            else
                row.createCell(5).setCellValue(getParentAnswerCount(i) + " (" + form.format(getParentAnswerCount(i) / (double) UserManager.getParentCount(i) * 100) + "%)");
        }

        row = sheet.createRow(rowCount++);
        row.createCell(0).setCellValue("총합");
        row.createCell(5).setCellValue(getParentAnswerCount() + " (" + form.format(getParentAnswerCount() / (double) UserManager.getParentCount() * 100) + "%)");

        //int columIndexCount = getNumberOfColumnIndexes();
        return sheet;
    }

    private XSSFSheet createSecondSheet(XSSFSheet sheet) throws SQLException {
        int rowCount = 0;
        int cellCount = 0;
        XSSFRow row = sheet.createRow(rowCount++);
        Iterator<String> questionIterator = items.iterator();
        row.createCell(cellCount++).setCellValue("학번");
        row.createCell(cellCount++).setCellValue("학생/학부모");
        row.createCell(cellCount++).setCellValue("이름");
        while (questionIterator.hasNext()) {
            row.createCell(cellCount++).setCellValue(questionIterator.next());
        }

        Iterator<Integer> stuNumIterator = UserManager.getStuNumIterator();

        while (stuNumIterator.hasNext()) {
            int stuNum = stuNumIterator.next();

            ResultSet studentRs = DBManager.execute("SELECT uid, name FROM USER WHERE identity = 'child' AND stuNum = ?", stuNum);
            if (studentRs.next()) {
                row = sheet.createRow(rowCount++);
                row.createCell(0).setCellValue(stuNum);
                row.createCell(1).setCellValue("학생");
                row.createCell(2).setCellValue(studentRs.getString("name"));
                ResultSet answerRs = DBManager.execute("SELECT answer FROM surveyAnswer WHERE letterNumber = ? AND uid = ? ORDER BY columnIndex;", letterNumber, studentRs.getString("uid"));
                cellCount = 3;
                int questionNumber = 1;
                System.out.println(answerRs.isLast());
                System.out.println(answerRs.isAfterLast());
                if (answerRs.next()) {
                    do {
                        int a = answerRs.getInt("answer");
                        System.out.println("ANSWER: " + a);
                        String answer = "응답없음";
                        if (a == 1) answer = (String) ((List) answerForms.get(questionNumber - 1)).get(0);
                        if (a == 2) answer = (String) ((List) answerForms.get(questionNumber - 1)).get(1);
                        if (a == 3) answer = (String) ((List) answerForms.get(questionNumber - 1)).get(2);
                        if (a == 4) answer = (String) ((List) answerForms.get(questionNumber - 1)).get(3);
                        if (a == 5) answer = (String) ((List) answerForms.get(questionNumber - 1)).get(4);
                        row.createCell(cellCount++).setCellValue(answer);
                        questionNumber++;
                    } while (answerRs.next());
                } else {
                    for (int i = 0; i < getNumberOfColumnIndexes(); i++) {
                        row.createCell(cellCount++).setCellValue("미응답");
                    }
                }
            }

            ResultSet parentRs = DBManager.execute("SELECT uid, name FROM USER WHERE identity = 'parent' AND stuNum = ?", stuNum);
            if (parentRs.next()) {
                row = sheet.createRow(rowCount++);
                row.createCell(0).setCellValue(stuNum);
                row.createCell(1).setCellValue("학부모");
                row.createCell(2).setCellValue(parentRs.getString("name"));
                ResultSet answerRs = DBManager.execute("SELECT answer FROM surveyAnswer WHERE letterNumber = ? AND uid = ? ORDER BY columnIndex;", letterNumber, parentRs.getString("uid"));
                cellCount = 3;
                int questionNumber = 1;
                if (answerRs.next()) {
                    do {
                        int a = answerRs.getInt("answer");
                        String answer = "응답없음";
                        if (a == 1) answer = (String) ((List) answerForms.get(questionNumber - 1)).get(0);
                        if (a == 2) answer = (String) ((List) answerForms.get(questionNumber - 1)).get(1);
                        if (a == 3) answer = (String) ((List) answerForms.get(questionNumber - 1)).get(2);
                        if (a == 4) answer = (String) ((List) answerForms.get(questionNumber - 1)).get(3);
                        if (a == 5) answer = (String) ((List) answerForms.get(questionNumber - 1)).get(4);
                        row.createCell(cellCount++).setCellValue(answer);
                        questionNumber++;
                    } while (answerRs.next());
                } else {
                    for (int i = 0; i < getNumberOfColumnIndexes(); i++) {
                        row.createCell(cellCount++).setCellValue("미응답");
                    }
                }
            }
        }
        return sheet;
    }

    public boolean modifyAnswer(String uid, List answers, String answerDate) throws SQLException {
        Iterator<Integer> iterator = answers.iterator();
        int columnIndex = 1;
        while (iterator.hasNext()) {
            int answer = iterator.next();

            if (DBManager.update(ANSWER_MODIFY, answer, answerDate, uid, letterNumber, columnIndex++) == -1) {
                DBManager.rollback();
                return false;
            }
        }

        DBManager.commit();
        return true;
    }

    private int getParentAnswerCount() throws SQLException {
        ResultSet rs = DBManager.execute(ANSWER_COUNT_PARENT_ALL, letterNumber);
        rs.next();
        return rs.getInt("count");
    }

    private int getParentAnswerCount(int grade) throws SQLException {
        ResultSet rs = DBManager.execute(ANSWER_COUNT_PARENT, letterNumber, grade + "%");
        rs.next();
        return rs.getInt("count");
    }

    private int getParentAnswerCount(int grade, int Class) throws SQLException {
        ResultSet rs = DBManager.execute(ANSWER_COUNT_PARENT, letterNumber, grade + "0" + Class + "%");
        rs.next();
        return rs.getInt("count");
    }

    private int getStudentAnswerCount() throws SQLException {
        ResultSet rs = DBManager.execute(ANSWER_COUNT_STUDENT_ALL, letterNumber);
        rs.next();
        return rs.getInt("count");
    }

    private int getStudentAnswerCount(int grade) throws SQLException {
        ResultSet rs = DBManager.execute(ANSWER_COUNT_STUDENT, letterNumber, grade + "%");
        rs.next();
        return rs.getInt("count");
    }

    private int getStudentAnswerCount(int grade, int Class) throws SQLException {
        ResultSet rs = DBManager.execute(ANSWER_COUNT_STUDENT, letterNumber, grade + "0" + Class + "%");
        rs.next();
        return rs.getInt("count");
    }

    private int getNumberOfColumnIndexes() throws SQLException {
        ResultSet rs = DBManager.execute("SELECT COUNT(columnIndex) AS count FROM surveyQuestion WHERE letterNumber = ?", letterNumber);
        rs.next();
        return rs.getInt("count");
    }

    public static int getNextLetterNumber() throws SQLException {
        ResultSet rs = DBManager.execute("SELECT max(letterNumber) AS max FROM SURVEY");
        if (rs.next()) {
            return rs.getInt("max") + 1;
        } else return 1;
    }

    public List getAnswerForms() {
        return answerForms;
    }

    public void setAnswerForms(List answerForms) {
        this.answerForms = answerForms;
    }
}

