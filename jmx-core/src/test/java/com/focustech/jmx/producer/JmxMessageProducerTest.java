//package com.focustech.jmx.producer;
//
//import kafka.producer.KeyedMessage;
//
///*
// * ����kafka�Ƿ���ͨ�ܷ�����Ϣ��ָ����topic
// */
//public class JmxMessageProducerTest {
//    public static void main(String[] args) throws InterruptedException {
//        JmxMessageProducer jmxMessageProducer = JmxMessageProducer.getInstance();
//        int i = 0;
//        while (true) {
//            try {
//                i++;
//                KeyedMessage<Integer, String> message = new KeyedMessage<Integer, String>("jmx_server", "test_" + i);
//                jmxMessageProducer.getProducer().send(message);
//                Thread.sleep(1000);
//            }
//            catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }
//}
