CREATE TABLE `users` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `email_address` varchar(255) NOT NULL,
  `user_name` varchar(40) NOT NULL,
  `password` varchar(40) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY (`email_address`),
  UNIQUE KEY (`user_name`)
) ENGINE=InnoDB AUTO_INCREMENT=2635 DEFAULT CHARSET=utf8;