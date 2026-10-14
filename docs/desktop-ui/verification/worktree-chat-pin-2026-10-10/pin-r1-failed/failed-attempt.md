# Pin R1：失败尝试保留

控制器5/5、Store 6/7通过，1 error。HostId(" ")在构造list时提前抛出，未进入assertFailsWith；生产类型已拒绝非法身份。余下集成/UI/旧回归/build未执行。R2仅窄修测试断言位置，保留全部7方法与其他断言，R2验收仍以独立证据为准。
