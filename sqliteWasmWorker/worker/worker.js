// Adapted from the Apache-2.0 Room Web demo linked by the AndroidX Room 3 documentation.
import sqlite3InitModule from '@sqlite.org/sqlite-wasm';

let sqlite3 = null;

const databases = new Map();
const statements = new Map();
let nextDatabaseId = 0;
let nextStatementId = 0;

function postError(id, error) {
  postMessage({ id, error: error instanceof Error ? error.message : String(error) });
}

function openRequest(id, requestData) {
  try {
    const databaseId = nextDatabaseId++;
    const database = typeof sqlite3.oo1.OpfsDb === 'function'
      ? new sqlite3.oo1.OpfsDb(requestData.fileName)
      : new sqlite3.oo1.DB(requestData.fileName, 'ct');
    databases.set(databaseId, database);
    postMessage({ id, data: { databaseId } });
  } catch (error) {
    postError(id, error);
  }
}

function prepareRequest(id, requestData) {
  try {
    const database = databases.get(requestData.databaseId);
    if (!database) {
      postError(id, `Invalid database ID: ${requestData.databaseId}`);
      return;
    }

    const statementId = nextStatementId++;
    const statement = database.prepare(requestData.sql);
    statements.set(statementId, statement);

    const columnNames = [];
    for (let index = 0; index < statement.columnCount; index++) {
      columnNames.push(sqlite3.capi.sqlite3_column_name(statement, index));
    }

    postMessage({
      id,
      data: {
        statementId,
        parameterCount: sqlite3.capi.sqlite3_bind_parameter_count(statement),
        columnNames
      }
    });
  } catch (error) {
    postError(id, error);
  }
}

function stepRequest(id, requestData) {
  const statement = statements.get(requestData.statementId);
  if (!statement) {
    postError(id, `Invalid statement ID: ${requestData.statementId}`);
    return;
  }

  try {
    const rows = [];
    const columnTypes = [];
    statement.reset();
    statement.clearBindings();
    for (let index = 0; index < requestData.bindings.length; index++) {
      statement.bind(index + 1, requestData.bindings[index]);
    }
    while (statement.step()) {
      if (columnTypes.length === 0) {
        for (let index = 0; index < statement.columnCount; index++) {
          columnTypes.push(sqlite3.capi.sqlite3_column_type(statement, index));
        }
      }
      rows.push(statement.get([]));
    }
    postMessage({ id, data: { rows, columnTypes } });
  } catch (error) {
    postError(id, error);
  }
}

function closeRequest(id, requestData) {
  try {
    if (requestData.statementId != null) {
      const statement = statements.get(requestData.statementId);
      if (!statement) {
        postError(id, `Invalid statement ID: ${requestData.statementId}`);
        return;
      }
      statement.finalize();
      statements.delete(requestData.statementId);
    }

    if (requestData.databaseId != null) {
      const database = databases.get(requestData.databaseId);
      if (!database) {
        postError(id, `Invalid database ID: ${requestData.databaseId}`);
        return;
      }
      database.close();
      databases.delete(requestData.databaseId);
    }
  } catch (error) {
    postError(id, error);
  }
}

const commandMap = {
  open: openRequest,
  prepare: prepareRequest,
  step: stepRequest,
  close: closeRequest
};

function handleMessage(event) {
  const request = event.data;
  if (!request || request.data == null) {
    postError(request?.id, 'Invalid request: missing data.');
    return;
  }

  const handler = commandMap[request.data.cmd];
  if (!handler) {
    postError(request.id, `Invalid request: unknown command '${request.data.cmd}'.`);
    return;
  }
  handler(request.id, request.data);
}

const messageQueue = [];
onmessage = event => {
  if (sqlite3 == null) {
    messageQueue.push(event);
  } else {
    handleMessage(event);
  }
};

sqlite3InitModule().then(instance => {
  sqlite3 = instance;
  while (messageQueue.length > 0) {
    handleMessage(messageQueue.shift());
  }
});
