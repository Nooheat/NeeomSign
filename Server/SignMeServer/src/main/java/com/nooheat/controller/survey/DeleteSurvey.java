package com.nooheat.controller.survey;

import com.nooheat.manager.JWT;
import com.nooheat.model.ResponselessLetter;
import com.nooheat.model.Survey;
import com.nooheat.support.API;
import com.nooheat.support.Category;
import com.nooheat.support.URIMapping;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

import java.sql.SQLException;

/**
 * Created by NooHeat on 23/09/2017.
 */
@API(category = Category.SURVEY, summary = "설문조사 삭제", successCode = 200, failureCode = 400, etc = "잘못된 요청 : 400, 로그인 안됨 : 401, 삭제권한 없음(본인 글 아님) : 403, 서버오류 : 500")
@URIMapping(uri = "/survey/:letterNumber", method = HttpMethod.DELETE)
public class DeleteSurvey implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext ctx) {
        HttpServerRequest req = ctx.request();
        HttpServerResponse res = ctx.response();

        JWT token = JWT.verify(ctx);
        if (token == null) {
            res.setStatusCode(401).end();
            return;
        }
        if (token.isAdmin() == false) {
            res.setStatusCode(403).end();
            return;
        }

        int letterNumber = -1;
        try {
            letterNumber = Integer.parseInt(req.getParam("letterNumber"));
        } catch (NumberFormatException e) {
            res.setStatusCode(400).end();
            return;
        }


        String writerUid = token.getUid();

        Survey survey = null;
        try {
            survey = Survey.findOne(letterNumber);
        } catch (SQLException e) {
            res.setStatusCode(500).end();
            return;
        }


        if (survey == null) {
            res.setStatusCode(400).end();
            return;
        }

        if (!survey.getWriterUid().equals(token.getUid())) {
            res.setStatusCode(403).end();
            return;
        }


        boolean success = survey.delete();
        if (success) res.setStatusCode(200).end();
        else res.setStatusCode(500).end();


    }
}