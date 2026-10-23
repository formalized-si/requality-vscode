package com.unitesk.requality.eclipse.handlers.create;

import com.unitesk.requality.core.operation.create.CreateElementOperation;
import com.unitesk.requality.core.operation.create.CreateTestPurposeOperation;
import com.unitesk.requality.eclipse.handlers.NewElementHandler;

public class CreateTestPurposeHandler extends NewElementHandler {

    @Override
    protected CreateElementOperation getCreateElementOperation() {
        return CreateTestPurposeOperation.getOne();
    }

}
