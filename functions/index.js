// The Cloud Functions for Firebase SDK to create Cloud Functions and setup triggers.
const functions = require('firebase-functions');
// The Firebase Admin SDK to access Cloud Firestore.
const admin = require('firebase-admin');
const user = require('firebase-functions/lib/providers/auth');
var serviceAccount = require('./serviceAccountKey.json');
const Message = require('firebase-functions/lib/providers/pubsub');
const error = require('firebase-functions/lib/logger');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: 'https://testssnceapp-38ee9.firebaseio.com'
});

exports.assignSharer = functions.database.ref('Bus Location Sharers/{routeNo}/')
.onCreate((snapshot) => {
    var data = snapshot.val();    

    if (snapshot.numChildren() === 1) {
        var userId = Object.keys(data)[0];
        var tokenId = Object.values(data[userId])[0];
        
        console.log('Encoded token ID:', tokenId);

        admin.auth().verifyIdToken(tokenId)
            .then(function(decodedToken) {
                console.log('Decoded Token: ', decodedToken, 'Attempting to send msg 1...');
                // var payload1 = {
                //     "data" : {
                //         title : userId,
                //         message : 'start-sharing-request'
                //     },
                //     "token" : tokenId
                // };
                // admin.messaging().send(payload1).then((response) => {
                //     return console.log('1 ---> Sent start command to token ID: ', decodedToken, ' and the message is: ', payload1);
                // }).catch((error) => {
                //     resp = console.log('1 ---> Error sending msg: ', error);
                // });
                // console.log('Attempting to send msg 2...');
                // var payload2 = {
                //     "data" : {
                //         title : userId,
                //         message : 'start-sharing-request'
                //     },
                //     "token" : decodedToken
                // };
                // admin.messaging().send(payload2).then((response) => {
                //     return console.log('2 ---> Sent start command to token ID: ', decodedToken, ' and the message is: ', payload2);
                // }).catch((error) => {
                //     resp = console.log('2 ---> Error sending msg: ', error);
                // });
                var payload3 = {
                    "data" : {
                        title : userId,
                        message : 'start-sharing-request'
                    },
                    "token" : userId
                };
                admin.messaging().send(payload3).then((response) => {
                    return console.log('3 ---> Sent start command to token ID: ', decodedToken, ' and the message is: ', payload3);
                }).catch((error) => {
                    resp = console.log('3 ---> Error sending msg: ', error);
                });
                return null;
        }).catch(function(error) {
                console.log('', error);
            });
        
    }
    return null;
});